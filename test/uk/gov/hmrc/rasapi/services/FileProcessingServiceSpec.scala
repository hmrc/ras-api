/*
 * Copyright 2026 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.rasapi.services

import org.apache.pekko.stream.Materializer
import org.apache.pekko.actor.ActorSystem
import java.time.{Instant, LocalDate}
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{eq => Meq, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.AnyContentAsEmpty
import play.api.test.FakeRequest
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.cache.CacheItem
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.connectors.{DesConnector, UpscanConnector}
import uk.gov.hmrc.rasapi.helpers.ResidencyYearResolver
import uk.gov.hmrc.rasapi.metrics.Metrics
import uk.gov.hmrc.rasapi.models._
import uk.gov.hmrc.rasapi.repositories.RepositoriesHelper.getAll
import uk.gov.hmrc.rasapi.repositories.TestFileWriter
import uk.gov.hmrc.rasapi.repository.RasFilesRepository

import java.io.{ByteArrayInputStream, FileInputStream}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class FileProcessingServiceSpec extends AnyWordSpecLike
  with Matchers
  with GuiceOneAppPerSuite
  with ScalaFutures
  with MockitoSugar
  with BeforeAndAfter
  with DefaultPlayMongoRepositorySupport[Chunks] {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  implicit val fakeReq: FakeRequest[AnyContentAsEmpty.type] = FakeRequest("POST", "/residency-status")
  implicit lazy val system: ActorSystem        = ActorSystem()
  implicit val materializers: Materializer = Materializer(system)

  val mockUpscanConnector: UpscanConnector = mock[UpscanConnector]

  val mockDesConnector: DesConnector = mock[DesConnector]
  val mockSessionCache: RasFilesSessionService = mock[RasFilesSessionService]
  val mockResidencyYearResolver: ResidencyYearResolver = mock[ResidencyYearResolver]
  val mockAuditService: AuditService = mock[AuditService]
  val appContext: AppContext = app.injector.instanceOf[AppContext]
  val metrics: Metrics = app.injector.instanceOf[Metrics]

  val STATUS_DECEASED: String = "DECEASED"
  val STATUS_MATCHING_FAILED: String = "STATUS_UNAVAILABLE"
  val STATUS_INTERNAL_SERVER_ERROR: String = "INTERNAL_SERVER_ERROR"
  val STATUS_SERVICE_UNAVAILABLE: String = "SERVICE_UNAVAILABLE"
  val STATUS_FILE_PROCESSING_MATCHING_FAILED: String = "cannot_provide_status"
  val STATUS_FILE_PROCESSING_INTERNAL_SERVER_ERROR: String = "problem-getting-status"
  override lazy val repository = new RasFilesRepository(mongoComponent, appContext)

  val SUT: FileProcessingService = new FileProcessingService (
    mockUpscanConnector,
    mockDesConnector,
    mockResidencyYearResolver,
    mockAuditService,
    mockSessionCache,
    repository,
    appContext,
    metrics,
    ExecutionContext.global
  ) {
    override def getCurrentDate: LocalDate = LocalDate.parse("2018-06-04", DateTimeFormatter.ofPattern("yyyy-MM-dd"))

    override val allowDefaultRUK: Boolean = false

    override val DECEASED: String = STATUS_DECEASED
    override val MATCHING_FAILED: String = STATUS_MATCHING_FAILED
    override val INTERNAL_SERVER_ERROR: String = STATUS_INTERNAL_SERVER_ERROR
    override val FILE_PROCESSING_MATCHING_FAILED: String = STATUS_FILE_PROCESSING_MATCHING_FAILED
    override val FILE_PROCESSING_INTERNAL_SERVER_ERROR: String = STATUS_FILE_PROCESSING_INTERNAL_SERVER_ERROR
    override val SERVICE_UNAVAILABLE: String = STATUS_SERVICE_UNAVAILABLE
  }

  def getTestFilePath: Path = {
    val successresultsArr = Array("LE241131B,Jim,Jimson,1990-02-21",
      "LE241131B,GARY,BRAVO,1990-02-21",
      "LE241131B,SIMON,DAWSON,1990-02-21",
      "LE241131B,MICHEAL,SLATER,1990-02-21"
    )
    TestFileWriter.generateFile(successresultsArr.iterator)
  }

  val userId: String = "A1234567"
  val fileId: String = "file-id-1234"

  when(mockDesConnector.otherUk).thenReturn("otherUKResident")
  when(mockDesConnector.scotRes).thenReturn("scotResident")

  before {
    reset(mockUpscanConnector)
    reset(mockDesConnector)
    reset(mockResidencyYearResolver)
    reset(mockAuditService)
    reset(mockSessionCache)
  }

  "fetchResult" should {
    "successfully audit processed data" when {
      "there is a successful result returned when a request is made on 4th Feb 2018" in {

        val testFilePath = getTestFilePath

        val SUT = new FileProcessingService (
          mockUpscanConnector,
          mockDesConnector,
          mockResidencyYearResolver,
          mockAuditService,
          mockSessionCache,
          repository,
          appContext,
          metrics,
          ExecutionContext.global
        ) {
          override def getCurrentDate: LocalDate = LocalDate.parse("2018-02-04", DateTimeFormatter.ofPattern("yyyy-MM-dd"))

          override val allowDefaultRUK: Boolean = true

          override val DECEASED: String = STATUS_DECEASED
          override val MATCHING_FAILED: String = STATUS_MATCHING_FAILED
          override val INTERNAL_SERVER_ERROR: String = STATUS_INTERNAL_SERVER_ERROR
          override val FILE_PROCESSING_MATCHING_FAILED: String = STATUS_FILE_PROCESSING_MATCHING_FAILED
          override val FILE_PROCESSING_INTERNAL_SERVER_ERROR: String = STATUS_FILE_PROCESSING_INTERNAL_SERVER_ERROR
          override val SERVICE_UNAVAILABLE: String = STATUS_SERVICE_UNAVAILABLE
        }

        when(mockUpscanConnector.getUpscanFile(any(), any(), any())).thenReturn(Future.successful(Some(new FileInputStream(testFilePath.toFile))))

        when(mockDesConnector.otherUk).thenReturn("otherUKResident")
        when(mockDesConnector.scotRes).thenReturn("scotResident")

        val expectedResultsFile = "National Insurance number,First name,Last name,Date of birth,2017 to 2018 residency status,2018 to 2019 residency status" +
          "LE241131B,Jim,Jimson,1990-02-21,otherUKResident,otherUKResident" +
          "LE241131B,GARY,BRAVO,1990-02-21,otherUKResident,otherUKResident" +
          "LE241131B,SIMON,DAWSON,1990-02-21,otherUKResident,otherUKResident" +
          "LE241131B,MICHEAL,SLATER,1990-02-21,otherUKResident,otherUKResident"

        val callbackData: UpscanCallbackData = UpscanCallbackData(reference = fileId, downloadUrl = Some("url"), fileStatus = "READY", uploadDetails = None, failureDetails = None)
        val cacheItem = CacheItem("sessionValue", Json.toJson(Map("user1234" -> Json.toJson(callbackData))).as[JsObject], Instant.now, Instant.now)

        when(mockSessionCache.updateFileSession(any(), any(), any(), any()))
          .thenReturn(Future.successful(cacheItem))

        when(mockDesConnector.getResidencyStatus(any[IndividualDetails], any(), any(), any())).thenReturn(
          Future.successful(Left(ResidencyStatus("scotResident", Some("otherUKResident")))))

        when(mockResidencyYearResolver.isBetweenJanAndApril).thenReturn(true)

        val test = SUT.processFile("user1234", callbackData, V2_0)

        Thread.sleep(5000)

        val res = await(repository.fetchFile(fileId, userId))
        var result = new String("")
        await(res.get.data.runWith(getAll.mapMaterializedValue {
          _.map { bytes =>
            result =
              result.concat(new String(bytes))
          }
        }))
        result.replaceAll("(\\r|\\n)", "") shouldBe expectedResultsFile.mkString

        Files.deleteIfExists(testFilePath)

        verify(mockAuditService, times(4)).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/residency-status"),
          auditData = Meq(Map("successfulLookup" -> "true",
            "CYStatus" -> "otherUKResident",
            "NextCYStatus" -> "otherUKResident",
            "fileId" -> fileId,
            "nino" -> "LE241131B",
            "userIdentifier" -> "user1234",
            "requestSource" -> "FE_BULK"))
        )(any())
      }

      "there is a successful result returned when a request is made on 4th Feb 2019" in {

        val testFilePath = getTestFilePath

        val SUT = new FileProcessingService (
          mockUpscanConnector,
          mockDesConnector,
          mockResidencyYearResolver,
          mockAuditService,
          mockSessionCache,
          repository,
          appContext,
          metrics,
          ExecutionContext.global
        ) {
          override def getCurrentDate: LocalDate = LocalDate.parse("2019-02-04", DateTimeFormatter.ofPattern("yyyy-MM-dd"))

          override val allowDefaultRUK: Boolean = true

          override val DECEASED: String = STATUS_DECEASED
          override val MATCHING_FAILED: String = STATUS_MATCHING_FAILED
          override val INTERNAL_SERVER_ERROR: String = STATUS_INTERNAL_SERVER_ERROR
          override val FILE_PROCESSING_MATCHING_FAILED: String = STATUS_FILE_PROCESSING_MATCHING_FAILED
          override val FILE_PROCESSING_INTERNAL_SERVER_ERROR: String = STATUS_FILE_PROCESSING_INTERNAL_SERVER_ERROR
          override val SERVICE_UNAVAILABLE: String = STATUS_SERVICE_UNAVAILABLE
        }

        when(mockUpscanConnector.getUpscanFile(any(), any(), any())).thenReturn(Future.successful(Some(new FileInputStream(testFilePath.toFile))))

        when(mockDesConnector.otherUk).thenReturn("otherUKResident")
        when(mockDesConnector.scotRes).thenReturn("scotResident")

        val expectedResultsFile = "National Insurance number,First name,Last name,Date of birth,2018 to 2019 residency status,2019 to 2020 residency status" +
          "LE241131B,Jim,Jimson,1990-02-21,scotResident,scotResident" +
          "LE241131B,GARY,BRAVO,1990-02-21,scotResident,scotResident" +
          "LE241131B,SIMON,DAWSON,1990-02-21,scotResident,scotResident" +
          "LE241131B,MICHEAL,SLATER,1990-02-21,scotResident,scotResident"

        val callbackData: UpscanCallbackData = UpscanCallbackData(reference = fileId, downloadUrl = Some("url"), fileStatus = "READY", uploadDetails = None, failureDetails = None)
        val cacheItem = CacheItem("sessionValue", Json.toJson(Map("user1234" -> Json.toJson(callbackData))).as[JsObject], Instant.now, Instant.now)

        when(mockSessionCache.updateFileSession(any(), any(), any(), any()))
          .thenReturn(Future.successful(cacheItem))

        when(mockDesConnector.getResidencyStatus(any[IndividualDetails], any(), any(), any())).thenReturn(
          Future.successful(Left(ResidencyStatus("scotResident", Some("scotResident")))))

        when(mockResidencyYearResolver.isBetweenJanAndApril).thenReturn(true)

        SUT.processFile("user1234", callbackData, V2_0)

        Thread.sleep(20000)

        val res = await(repository.fetchFile(fileId, userId))
        var result = new String("")
        await(res.get.data.runWith(getAll.mapMaterializedValue {
          _.map { bytes =>
            result =
              result.concat(new String(bytes))
          }
        }))
        result.replaceAll("(\\r|\\n)", "") shouldBe expectedResultsFile.mkString
        Files.deleteIfExists(testFilePath)

        verify(mockAuditService, times(4)).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/residency-status"),
          auditData = Meq(Map("successfulLookup" -> "true",
            "CYStatus" -> "scotResident",
            "NextCYStatus" -> "scotResident",
            "fileId" -> fileId,
            "nino" -> "LE241131B",
            "userIdentifier" -> "user1234",
            "requestSource" -> "FE_BULK"))
        )(any())
      }

      "there is a successful result returned when a request is made on 4th June 2018" in {

        val testFilePath = getTestFilePath

        val SUT = new FileProcessingService (
          mockUpscanConnector,
          mockDesConnector,
          mockResidencyYearResolver,
          mockAuditService,
          mockSessionCache,
          repository,
          appContext,
          metrics,
          ExecutionContext.global
        ) {
          override def getCurrentDate: LocalDate = LocalDate.parse("2018-06-04", DateTimeFormatter.ofPattern("yyyy-MM-dd"))

          override val allowDefaultRUK: Boolean = true

          override val DECEASED: String = STATUS_DECEASED
          override val MATCHING_FAILED: String = STATUS_MATCHING_FAILED
          override val INTERNAL_SERVER_ERROR: String = STATUS_INTERNAL_SERVER_ERROR
          override val FILE_PROCESSING_MATCHING_FAILED: String = STATUS_FILE_PROCESSING_MATCHING_FAILED
          override val FILE_PROCESSING_INTERNAL_SERVER_ERROR: String = STATUS_FILE_PROCESSING_INTERNAL_SERVER_ERROR
          override val SERVICE_UNAVAILABLE: String = STATUS_SERVICE_UNAVAILABLE
        }

        when(mockUpscanConnector.getUpscanFile(any(), any(), any())).thenReturn(Future.successful(Some(new FileInputStream(testFilePath.toFile))))

        when(mockDesConnector.otherUk).thenReturn("otherUKResident")
        when(mockDesConnector.scotRes).thenReturn("scotResident")

        val expectedResultsFile = "National Insurance number,First name,Last name,Date of birth,2018 to 2019 residency status" +
          "LE241131B,Jim,Jimson,1990-02-21,otherUKResident" +
          "LE241131B,GARY,BRAVO,1990-02-21,otherUKResident" +
          "LE241131B,SIMON,DAWSON,1990-02-21,otherUKResident" +
          "LE241131B,MICHEAL,SLATER,1990-02-21,otherUKResident"

        val upscanCallbackData = UpscanCallbackData(reference = fileId, downloadUrl = Some("url"), fileStatus = "READY", uploadDetails = None, failureDetails = None)
        val cacheItem = CacheItem("sessionValue", Json.toJson(Map("user1234" -> Json.toJson(upscanCallbackData))).as[JsObject], Instant.now, Instant.now)

        when(mockSessionCache.updateFileSession(any(), any(), any(), any()))
          .thenReturn(Future.successful(cacheItem))

        when(mockDesConnector.getResidencyStatus(any[IndividualDetails], any(), any(), any())).thenReturn(
          Future.successful(Left(ResidencyStatus("otherUKResident", Some("scotResident")))))

        when(mockResidencyYearResolver.isBetweenJanAndApril).thenReturn(false)

        SUT.processFile("user1234", upscanCallbackData, V2_0)

        Thread.sleep(20000)

        val res = await(repository.fetchFile(fileId, userId))
        var result = new String("")
        await(res.get.data.runWith(getAll.mapMaterializedValue {
          _.map { bytes =>
            result =
              result.concat(new String(bytes))
          }
        }))
        result.replaceAll("(\\r|\\n)", "") shouldBe expectedResultsFile.mkString
        Files.deleteIfExists(testFilePath)

        verify(mockAuditService, times(4)).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/residency-status"),
          auditData = Meq(Map("successfulLookup" -> "true",
            "CYStatus" -> "otherUKResident",
            "fileId" -> fileId,
            "nino" -> "LE241131B",
            "userIdentifier" -> "user1234",
            "requestSource" -> "FE_BULK"))
        )(any())
      }

      "there is a matching failed result" in {

        val testFilePath = getTestFilePath

        val SUT = new FileProcessingService (
          mockUpscanConnector,
          mockDesConnector,
          mockResidencyYearResolver,
          mockAuditService,
          mockSessionCache,
          repository,
          appContext,
          metrics,
          ExecutionContext.global
        ) {
          override def getCurrentDate: LocalDate = LocalDate.parse("2018-02-04", DateTimeFormatter.ofPattern("yyyy-MM-dd"))

          override val allowDefaultRUK: Boolean = true

          override val DECEASED: String = STATUS_DECEASED
          override val MATCHING_FAILED: String = STATUS_MATCHING_FAILED
          override val INTERNAL_SERVER_ERROR: String = STATUS_INTERNAL_SERVER_ERROR
          override val FILE_PROCESSING_MATCHING_FAILED: String = STATUS_FILE_PROCESSING_MATCHING_FAILED
          override val FILE_PROCESSING_INTERNAL_SERVER_ERROR: String = STATUS_FILE_PROCESSING_INTERNAL_SERVER_ERROR
          override val SERVICE_UNAVAILABLE: String = STATUS_SERVICE_UNAVAILABLE
        }

        when(mockUpscanConnector.getUpscanFile(any(), any(), any())).thenReturn(Future.successful(Some(new FileInputStream(testFilePath.toFile))))

        when(mockDesConnector.otherUk).thenReturn("otherUKResident")
        when(mockDesConnector.scotRes).thenReturn("scotResident")

        val expectedResultsFile = "National Insurance number,First name,Last name,Date of birth,2017 to 2018 residency status,2018 to 2019 residency status" +
          s"LE241131B,Jim,Jimson,1990-02-21,$STATUS_FILE_PROCESSING_MATCHING_FAILED" +
          s"LE241131B,GARY,BRAVO,1990-02-21,$STATUS_FILE_PROCESSING_MATCHING_FAILED" +
          s"LE241131B,SIMON,DAWSON,1990-02-21,$STATUS_FILE_PROCESSING_MATCHING_FAILED" +
          s"LE241131B,MICHEAL,SLATER,1990-02-21,$STATUS_FILE_PROCESSING_MATCHING_FAILED"

        val upscanCallbackData = UpscanCallbackData(reference = fileId, downloadUrl = Some("url"), fileStatus = "READY", uploadDetails = None, failureDetails = None)
        val cacheItem = CacheItem("sessionValue", Json.toJson(Map("user1234" -> Json.toJson(upscanCallbackData))).as[JsObject], Instant.now, Instant.now)

        when(mockSessionCache.updateFileSession(any(), any(), any(), any()))
          .thenReturn(Future.successful(cacheItem))

        when(mockDesConnector.getResidencyStatus(any[IndividualDetails], any(), any(), any())).thenReturn(
          Future.successful(Right(ResidencyStatusFailure(code = s"$STATUS_MATCHING_FAILED", reason = s"$STATUS_MATCHING_FAILED"))))

        when(mockResidencyYearResolver.isBetweenJanAndApril).thenReturn(true)

        SUT.processFile("user1234", upscanCallbackData, V2_0)

        Thread.sleep(20000)

        val res = await(repository.fetchFile(fileId, userId))
        var result = new String("")
        await(res.get.data.runWith(getAll.mapMaterializedValue {
          _.map { bytes =>
            result =
              result.concat(new String(bytes))
          }
        }))
        result.replaceAll("(\\r|\\n)", "") shouldBe expectedResultsFile.mkString
        Files.deleteIfExists(testFilePath)

        verify(mockAuditService, times(4)).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/residency-status"),
          auditData = Meq(Map("nino" -> "LE241131B",
            "successfulLookup" -> "false",
            "fileId" -> fileId,
            "reason" -> "MATCHING_FAILED",
            "userIdentifier" -> "user1234",
            "requestSource" -> "FE_BULK"))
        )(any())
      }

      "there is a deceased result" in {

        val testFilePath = getTestFilePath

        val SUT = new FileProcessingService (
          mockUpscanConnector,
          mockDesConnector,
          mockResidencyYearResolver,
          mockAuditService,
          mockSessionCache,
          repository,
          appContext,
          metrics,
          ExecutionContext.global
        ) {
          override def getCurrentDate: LocalDate = LocalDate.parse("2018-02-04", DateTimeFormatter.ofPattern("yyyy-MM-dd"))

          override val allowDefaultRUK: Boolean = true

          override val DECEASED: String = STATUS_DECEASED
          override val MATCHING_FAILED: String = STATUS_MATCHING_FAILED
          override val INTERNAL_SERVER_ERROR: String = STATUS_INTERNAL_SERVER_ERROR
          override val FILE_PROCESSING_MATCHING_FAILED: String = STATUS_FILE_PROCESSING_MATCHING_FAILED
          override val FILE_PROCESSING_INTERNAL_SERVER_ERROR: String = STATUS_FILE_PROCESSING_INTERNAL_SERVER_ERROR
          override val SERVICE_UNAVAILABLE: String = STATUS_SERVICE_UNAVAILABLE
        }

        when(mockUpscanConnector.getUpscanFile(any(), any(), any())).thenReturn(Future.successful(Some(new FileInputStream(testFilePath.toFile))))

        when(mockDesConnector.otherUk).thenReturn("otherUKResident")
        when(mockDesConnector.scotRes).thenReturn("scotResident")

        val expectedResultsFile = "National Insurance number,First name,Last name,Date of birth,2017 to 2018 residency status,2018 to 2019 residency status" +
          s"LE241131B,Jim,Jimson,1990-02-21,$STATUS_FILE_PROCESSING_MATCHING_FAILED" +
          s"LE241131B,GARY,BRAVO,1990-02-21,$STATUS_FILE_PROCESSING_MATCHING_FAILED" +
          s"LE241131B,SIMON,DAWSON,1990-02-21,$STATUS_FILE_PROCESSING_MATCHING_FAILED" +
          s"LE241131B,MICHEAL,SLATER,1990-02-21,$STATUS_FILE_PROCESSING_MATCHING_FAILED"

        val callbackData: UpscanCallbackData = UpscanCallbackData(reference = fileId, downloadUrl = Some("url"), fileStatus = "READY", uploadDetails = None, failureDetails = None)
        val cacheItem = CacheItem("sessionValue", Json.toJson(Map("user1234" -> Json.toJson(callbackData))).as[JsObject], Instant.now, Instant.now)

        when(mockSessionCache.updateFileSession(any(), any(), any(), any()))
          .thenReturn(Future.successful(cacheItem))

        when(mockDesConnector.getResidencyStatus(any[IndividualDetails], any(), any(), any())).thenReturn(
          Future.successful(Right(ResidencyStatusFailure(code = s"$STATUS_DECEASED", reason = s"$STATUS_DECEASED"))))

        when(mockResidencyYearResolver.isBetweenJanAndApril).thenReturn(true)

        SUT.processFile("user1234", callbackData, V2_0)

        Thread.sleep(20000)

        val res = await(repository.fetchFile(fileId, userId))
        var result = new String("")
        await(res.get.data.runWith(getAll.mapMaterializedValue {
          _.map { bytes =>
            result =
              result.concat(new String(bytes))
          }
        }))
        result.replaceAll("(\\r|\\n)", "") shouldBe expectedResultsFile.mkString
        Files.deleteIfExists(testFilePath)

        verify(mockAuditService, times(4)).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/residency-status"),
          auditData = Meq(Map("nino" -> "LE241131B",
            "successfulLookup" -> "false",
            "fileId" -> fileId,
            "reason" -> s"$STATUS_DECEASED",
            "userIdentifier" -> "user1234",
            "requestSource" -> "FE_BULK"))
        )(any())
      }

      "there is a SERVICE_UNAVAILABLE result" in {
        val testFilePath = getTestFilePath

        val SUT = new FileProcessingService (
          mockUpscanConnector,
          mockDesConnector,
          mockResidencyYearResolver,
          mockAuditService,
          mockSessionCache,
          repository,
          appContext,
          metrics,
          ExecutionContext.global
        ) {
          override def getCurrentDate: LocalDate = LocalDate.parse("2018-02-04", DateTimeFormatter.ofPattern("yyyy-MM-dd"))

          override val allowDefaultRUK: Boolean = true

          override val DECEASED: String = STATUS_DECEASED
          override val MATCHING_FAILED: String = STATUS_MATCHING_FAILED
          override val INTERNAL_SERVER_ERROR: String = STATUS_INTERNAL_SERVER_ERROR
          override val FILE_PROCESSING_MATCHING_FAILED: String = STATUS_FILE_PROCESSING_MATCHING_FAILED
          override val FILE_PROCESSING_INTERNAL_SERVER_ERROR: String = STATUS_FILE_PROCESSING_INTERNAL_SERVER_ERROR
          override val SERVICE_UNAVAILABLE: String = STATUS_SERVICE_UNAVAILABLE
        }

        when(mockUpscanConnector.getUpscanFile(any(), any(), any())).thenReturn(Future.successful(Some(new FileInputStream(testFilePath.toFile))))

        when(mockDesConnector.otherUk).thenReturn("otherUKResident")
        when(mockDesConnector.scotRes).thenReturn("scotResident")

        val expectedResultsFile = "National Insurance number,First name,Last name,Date of birth,2017 to 2018 residency status,2018 to 2019 residency status" +
          s"LE241131B,Jim,Jimson,1990-02-21,$STATUS_FILE_PROCESSING_INTERNAL_SERVER_ERROR" +
          s"LE241131B,GARY,BRAVO,1990-02-21,$STATUS_FILE_PROCESSING_INTERNAL_SERVER_ERROR" +
          s"LE241131B,SIMON,DAWSON,1990-02-21,$STATUS_FILE_PROCESSING_INTERNAL_SERVER_ERROR" +
          s"LE241131B,MICHEAL,SLATER,1990-02-21,$STATUS_FILE_PROCESSING_INTERNAL_SERVER_ERROR"

        val callbackData: UpscanCallbackData = UpscanCallbackData(reference = fileId, downloadUrl = Some("url"), fileStatus = "READY", uploadDetails = None, failureDetails = None)
        val cacheItem = CacheItem("sessionValue", Json.toJson(Map("user1234" -> Json.toJson(callbackData))).as[JsObject], Instant.now, Instant.now)

        when(mockSessionCache.updateFileSession(any(), any(), any(), any()))
          .thenReturn(Future.successful(cacheItem))

        when(mockDesConnector.getResidencyStatus(any[IndividualDetails], any(), any(), any())).thenReturn(
          Future.successful(Right(ResidencyStatusFailure(code = s"$STATUS_SERVICE_UNAVAILABLE", reason = s"$STATUS_SERVICE_UNAVAILABLE"))))

        when(mockResidencyYearResolver.isBetweenJanAndApril).thenReturn(true)

        SUT.processFile("user1234", callbackData, V2_0)

        Thread.sleep(20000)

        val res = await(repository.fetchFile(fileId, userId))
        var result = new String("")
        await(res.get.data.runWith(getAll.mapMaterializedValue {
          _.map { bytes =>
            result =
              result.concat(new String(bytes))
          }
        }))
        result.replaceAll("(\\r|\\n)", "") shouldBe expectedResultsFile.mkString
        Files.deleteIfExists(testFilePath)

        verify(mockAuditService, times(4)).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/residency-status"),
          auditData = Meq(Map("nino" -> "LE241131B",
            "successfulLookup" -> "false",
            "fileId" -> fileId,
            "reason" -> s"$STATUS_SERVICE_UNAVAILABLE",
            "userIdentifier" -> "user1234",
            "requestSource" -> "FE_BULK"))
        )(any())
      }
    }
  }

  "FileProcessingService" should {
    "readFile" when {
      "ISO_8859_1 file with accented characters" in {

        val reference: String = "0b215e97-11d4-4006-91db-c067e74fc656"
        val fileId: String = "file-id-1"

        val row1 = "Johné,Smithè,AB123456C,1990-02-21".getBytes(StandardCharsets.ISO_8859_1)
        val inputStream = new ByteArrayInputStream(row1)

        when(mockUpscanConnector.getUpscanFile(any(), any(), any())).thenReturn(Future.successful(Some(inputStream)))

        val result = await(SUT.readFile(reference, fileId, userId))

        result.toList should contain theSameElementsAs List("Johné,Smithè,AB123456C,1990-02-21")
      }
    }

    "createMatchingData" when {

      "parse line as raw data and convert to RawMemberDetails object when there are 4 columns with at least one containing empty data" in {
        val inputData = ",Smith,AB123456C,90-02-21"

        val result = SUT.createMatchingData(inputData)

        result shouldBe Right(List("lastName-INVALID_FORMAT", "dateOfBirth-INVALID_FORMAT", "nino-MISSING_FIELD"))
      }

      "parse line as raw data and convert to RawMemberDetails object when there are less than 3 columns" in {
        val inputData = "Smith,AB123456C,1996-02-21"

        val result = SUT.createMatchingData(inputData)

        result shouldBe Right(List("firstName-INVALID_FORMAT", "lastName-INVALID_FORMAT", "dateOfBirth-MISSING_FIELD", "nino-INVALID_FORMAT"))
      }

      "parse empty line as raw data and convert to RawMemberDetails object" in {
        val inputData = ",,"

        val result = SUT.createMatchingData(inputData)

        result shouldBe Right(List("firstName-MISSING_FIELD", "lastName-MISSING_FIELD", "dateOfBirth-MISSING_FIELD", "nino-MISSING_FIELD"))
      }

      "date format is dd/mm/yyyy" when {
        "parse line as raw data and convert to IndividualDetails object" in {
          val inputData = "AB123456C,John,Smith,21/02/1995"

          val result = SUT.createMatchingData(inputData)

          result shouldBe Left(IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1995-02-21", DateTimeFormatter.ofPattern("yyyy-MM-dd"))))
        }
      }

      "date format is yyyy/mm/dd" when {
        "parse line as raw data and convert to IndividualDetails object" in {
          val inputData = "AB123456C,John,Smith,1995/02/21"

          val result = SUT.createMatchingData(inputData)

          result shouldBe Left(IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1995-02-21", DateTimeFormatter.ofPattern("yyyy-MM-dd"))))
        }
      }

      "date format is dd-mm-yyyy" when {
        "parse line as raw data and convert to IndividualDetails object" in {
          val inputData = "AB123456C,John,Smith,21-02-1995"

          val result = SUT.createMatchingData(inputData)

          result shouldBe Left(IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1995-02-21", DateTimeFormatter.ofPattern("yyyy-MM-dd"))))
        }
      }

      "date format is yyyy-mm-dd" when {

        "parse line as raw data and convert to IndividualDetails object" in {
          val inputData = "AB123456C,John,Smith,1995-02-21"

          val result = SUT.createMatchingData(inputData)

          result shouldBe Left(IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1995-02-21", DateTimeFormatter.ofPattern("yyyy-MM-dd"))))
        }

        "parse line as raw data and convert to RawMemberDetails object when there is an invalid date" in {
          val inputData = "LE241131B,Jim,Jimson,1989-02-31"

          val result = SUT.createMatchingData(inputData)

          result shouldBe Right(List("dateOfBirth-INVALID_DATE"))
        }

        "parse line as raw data and convert to RawMemberDetails object when there is a caught invalid date" in {
          val inputData = "LE241131B,Jim,Jimson,1111-15-15"

          val result = SUT.createMatchingData(inputData)

          result shouldBe Right(List("dateOfBirth-INVALID_DATE"))
        }

        "parse line as raw data and convert to RawMemberDetails object when there is a date not in yyyy-mm-dd format" in {
          val inputData = "LE241131B,Jim,Jimson,89-09-29"

          val result = SUT.createMatchingData(inputData)

          result shouldBe Right(List("dateOfBirth-INVALID_FORMAT"))
        }

        "parse line as raw data and convert to RawMemberDetails object when there is a date of birth in the future" in {
          val inputData = "LE241131B,Jim,Jimson,2099-01-01"

          val result = SUT.createMatchingData(inputData)

          result shouldBe Right(List("dateOfBirth-INVALID_DATE"))
        }

        "parse line as raw data and convert to RawMemberDetails object when there is a missing date" in {
          val inputData = "LE241131B,Jim,Jimson,"

          val result = SUT.createMatchingData(inputData)

          result shouldBe Right(List("dateOfBirth-MISSING_FIELD"))
        }
      }
    }

    val data = IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1992-02-21", DateTimeFormatter.ofPattern("yyyy-MM-dd")))

    "fetch result" when {
      "input row is valid and the date of processing is between january and april" in {
        when(mockDesConnector.getResidencyStatus(data, userId, V2_0, isBulkRequest = true)).thenReturn(
          Future.successful(Left(ResidencyStatus("otherUKResident", Some("scotResident")))))

        when(mockResidencyYearResolver.isBetweenJanAndApril).thenReturn(true)

        val inputRow = "AB123456C,John,Smith,1992-02-21"
        val result = SUT.fetchResult(inputRow, userId, fileId, V2_0)
        result shouldBe "AB123456C,John,Smith,1992-02-21,otherUKResident,scotResident"
      }

      "input row is valid and the date of processing is between april and december" in {
        when(mockDesConnector.getResidencyStatus(data, userId, V2_0, isBulkRequest = true)).thenReturn(
          Future.successful(Left(ResidencyStatus("otherUKResident", Some("scotResident")))))

        when(mockResidencyYearResolver.isBetweenJanAndApril).thenReturn(false)

        val inputRow = "AB123456C,John,Smith,1992-02-21"
        val result = SUT.fetchResult(inputRow, userId, fileId, V2_0)
        result shouldBe "AB123456C,John,Smith,1992-02-21,otherUKResident"
      }

      "input row matching failed" in {
        when(mockDesConnector.getResidencyStatus(data, userId, V2_0, isBulkRequest = true)).thenReturn(
          Future.successful(Right(ResidencyStatusFailure(STATUS_MATCHING_FAILED, STATUS_MATCHING_FAILED))))
        val inputRow = "AB123456C,John,Smith,1992-02-21"
        val result = SUT.fetchResult(inputRow, userId, fileId, V2_0)
        result shouldBe s"AB123456C,John,Smith,1992-02-21,$STATUS_FILE_PROCESSING_MATCHING_FAILED"
      }

      "input row returns deceased" in {
        when(mockDesConnector.getResidencyStatus(data, userId, V2_0, isBulkRequest = true)).thenReturn(
          Future.successful(Right(ResidencyStatusFailure(STATUS_DECEASED, STATUS_DECEASED))))
        val inputRow = "AB123456C,John,Smith,1992-02-21"
        val result = SUT.fetchResult(inputRow, userId, fileId, V2_0)
        result shouldBe s"AB123456C,John,Smith,1992-02-21,$STATUS_FILE_PROCESSING_MATCHING_FAILED"
      }

      "input row is inValid" in {
        val inputRow = "456C,John,Smith,1994-02-21"
        val result = SUT.fetchResult(inputRow, userId, fileId, V2_0)
        result shouldBe "456C,John,Smith,1994-02-21,nino-INVALID_FORMAT"
      }
    }

    "process file and generate results file " when {
      "valid file is submitted by user" in {

        val testFilePath = getTestFilePath

        when(mockUpscanConnector.getUpscanFile(any(), any(), any())).thenReturn(Future.successful(Some(new FileInputStream(testFilePath.toFile))))

        val expectedResultsFile = "National Insurance number,First name,Last name,Date of birth,2017 to 2018 residency status,2018 to 2019 residency status" +
          "LE241131B,Jim,Jimson,1990-02-21,otherUKResident,scotResident" +
          "LE241131B,GARY,BRAVO,1990-02-21,otherUKResident,scotResident" +
          "LE241131B,SIMON,DAWSON,1990-02-21,otherUKResident,scotResident" +
          "LE241131B,MICHEAL,SLATER,1990-02-21,otherUKResident,scotResident"


        val upscanCallbackData = UpscanCallbackData(reference = fileId, downloadUrl = Some("url"), fileStatus = "READY", uploadDetails = None, failureDetails = None)
        val cacheItem = CacheItem("sessionValue", Json.toJson(Map("user1234" -> Json.toJson(upscanCallbackData))).as[JsObject], Instant.now, Instant.now)

        when(mockSessionCache.updateFileSession(any(), any(), any(), any()))
          .thenReturn(Future.successful(cacheItem))

        when(mockDesConnector.getResidencyStatus(any[IndividualDetails], any(), any(), any())).thenReturn(
          Future.successful(Left(ResidencyStatus("otherUKResident", Some("scotResident")))))

        when(mockResidencyYearResolver.isBetweenJanAndApril).thenReturn(true)

        SUT.processFile("user1234", upscanCallbackData, V2_0)


        Thread.sleep(5000)

        val res = await(repository.fetchFile(fileId, userId))
        var result = new String("")
        await(res.get.data.runWith(getAll.mapMaterializedValue {
          _.map { bytes =>
            result =
              result.concat(new String(bytes))
          }
        }))
        result.replaceAll("(\\r|\\n)", "") shouldBe expectedResultsFile.mkString
        Files.deleteIfExists(testFilePath)
      }
    }

    "return status of error" when {
      "there is a problem manipulating the file" in {

        val callbackData: UpscanCallbackData = UpscanCallbackData(reference = "reference", downloadUrl = Some("url"), fileStatus = "FAILED", uploadDetails = None, failureDetails = None)
        val inputFileData = Try(Iterator("\"LE241131B,Jim,Jimson,1990-02-21\""))

        SUT.manipulateFile(inputFileData, "user1234", callbackData, V2_0)

        val captor: ArgumentCaptor[UpscanCallbackData] = ArgumentCaptor.forClass(classOf[UpscanCallbackData])
        verify(mockSessionCache, times(1)).updateFileSession(any(), captor.capture, any(), any())

        val resultsFileMetaData = captor.getValue
        resultsFileMetaData.fileStatus shouldBe "FAILED"
      }
    }
  }
}
