/*
 * Copyright 2023 HM Revenue & Customs
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

package uk.gov.hmrc.rasapi.controllers

import akka.actor.ActorSystem
import akka.stream.Materializer
import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{any, eq => Meq}
import org.mockito.Mockito.{reset, verify, when}
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status
import play.api.http.Status.UNAUTHORIZED
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.iteratee.Enumerator
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.{await, defaultAwaitTimeout, status}
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.rasapi.metrics.Metrics
import uk.gov.hmrc.rasapi.repository.{FileData, RasChunksRepository, RasFilesRepository}
import uk.gov.hmrc.rasapi.services.AuditService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Try}

class FileControllerSpec extends AnyWordSpecLike with Matchers with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfter {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  private val enrolmentIdentifier1 = EnrolmentIdentifier("PSAID", "A123456")
  private val enrolment1 = new Enrolment(key = "HMRC-PSA-ORG", identifiers = List(enrolmentIdentifier1), state = "Activated", None)
  private val enrolmentIdentifier2 = EnrolmentIdentifier("PPID", "A123456")
  private val enrolment2 = new Enrolment(key = "HMRC-PP-ORG", identifiers = List(enrolmentIdentifier2), state = "Activated", None)
  private val enrolmentIdentifier3 = EnrolmentIdentifier("PSAID", "A123456")
  private val enrolment3 = new Enrolment(key = "HMRC-PODS-ORG", identifiers = List(enrolmentIdentifier3), state = "Activated", None)
  private val enrolmentIdentifier4 = EnrolmentIdentifier("PSPID", "A123456")
  private val enrolment4 = new Enrolment(key = "HMRC-PODSPP-ORG", identifiers = List(enrolmentIdentifier4), state = "Activated", None)
  private val enrolments = Enrolments(Set(enrolment1,enrolment2,enrolment3,enrolment4))

  val successfulRetrieval: Future[Enrolments] = Future.successful(enrolments)
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockAuditService: AuditService = mock[AuditService]
  val mockRasChunksRepository: RasChunksRepository = mock[RasChunksRepository]
  val mockRasFileRepository: RasFilesRepository = mock[RasFilesRepository]
  val mockMetrics: Metrics = app.injector.instanceOf[Metrics]
  val mockCC: ControllerComponents = app.injector.instanceOf[ControllerComponents]

  val fileData: FileData = FileData(length = 124L,Enumerator("TEST START ".getBytes))

  val fileController: FileController = new FileController(
    mockRasFileRepository,
    mockRasChunksRepository,
    mockMetrics,
    mockAuditService,
    mockAuthConnector,
    mockCC,
    ExecutionContext.global
  ) {
    override def getFile(name: String, userId: String): Future[Option[FileData]] = Future(Some(fileData))
    override def deleteFile(name: String, userId: String): Future[Boolean] = Future(true)
  }

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .disable[com.kenshoo.play.metrics.PlayModule]
      .build()

  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val materializer: Materializer = app.materializer

  when(mockRasFileRepository.removeFile(any(), any())).thenReturn(Future.successful(true))

  before{
    reset(mockAuditService)
  }

  "FileController" should {
    "serve a file" when {
      "valid filename is provided" in {
        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

        val result = await(fileController.serveFile("testFile.csv").apply(FakeRequest(Helpers.GET, "/ras-api/file/getFile/:testFile")))
        result.header.status shouldBe Status.OK
        val headers = result.header.headers
        headers("Content-Length") shouldBe "124"
        headers("Content-Type") shouldBe "application/csv"
        headers("Content-Disposition") shouldBe "attachment; filename=\"testFile.csv\""
      }
    }

    "give NOT_FOUND response" when {
      val fileController = new FileController(
        mockRasFileRepository,
        mockRasChunksRepository,
        mockMetrics,
        mockAuditService,
        mockAuthConnector,
        mockCC,
        ExecutionContext.global
      ) {
        override def getFile(name: String, userId: String): Future[Option[FileData]] = Future(None)
      }

      "the file is not available" in {
        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

        val result = await(fileController.serveFile("testFile.csv").apply(FakeRequest(Helpers.GET, "/ras-api/file/getFile/:testFile")))
        result.header.status shouldBe Status.NOT_FOUND
      }
    }

    "return status 401 (Unauthorised)" when {
      "a valid lookup request has been submitted with no PSA or PP enrolments" in {

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(Future.failed(new InsufficientEnrolments))

        val result = fileController.serveFile("testFile.csv").apply(FakeRequest(Helpers.GET, "/ras-api/file/getFile/:testFile"))
        status(result) shouldBe UNAUTHORIZED
      }
    }

    "remove a file" when {
      "already saved fileName is provided" in {
        val fileController = new FileController (
          mockRasFileRepository,
          mockRasChunksRepository,
          mockMetrics,
          mockAuditService,
          mockAuthConnector,
          mockCC,
          ExecutionContext.global
        ){
          override def getFile(name: String, userId: String): Future[Option[FileData]] = Future(Some(fileData))
        }

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
        when(mockRasChunksRepository.removeChunk(any())).thenReturn(Future.successful(true))
        val fileName = "5b4628e02f00002501139c8c"
        val userId = "A123456"
        val result = await(fileController.remove(fileName, userId).apply(FakeRequest(Helpers.DELETE, s"/ras-api/file/remove/:$fileName/:$userId")))
        result.header.status shouldBe Status.OK
        verify(mockAuditService).audit(
          auditType = Meq("FileDeletion"),
          path = any(),
          auditData = Meq(Map("userIdentifier" -> "A123456",
            "fileName" -> fileName,
            "chunkDeletionSuccess" -> "true"))
        )(any())
      }

      "chunks deletion fails as id cannot be converted to a ObjectId" in {
        val fileController = new FileController (
          mockRasFileRepository,
          mockRasChunksRepository,
          mockMetrics,
          mockAuditService,
          mockAuthConnector,
          mockCC,
          ExecutionContext.global
        ){
          override def getFile(name: String, userId: String): Future[Option[FileData]] = Future(Some(fileData))

          override def parseStringIdToObjectId(id: String): Try[ObjectId] = Failure(new Throwable("Failure"))
        }

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
        when(mockRasChunksRepository.removeChunk(any())).thenReturn(Future.successful(true))
        val fileName = "testFile.csv"
        val userId = "A123456"
        val result = await(fileController.remove(fileName, userId).apply(FakeRequest(Helpers.DELETE, s"/ras-api/file/remove/:$fileName")))
        result.header.status shouldBe Status.OK

        verify(mockAuditService).audit(
          auditType = Meq("FileDeletion"),
          path = any(),
          auditData = Meq(Map("userIdentifier" -> userId,
            "fileName" -> fileName,
            "chunkDeletionSuccess" -> "false",
            "reason" -> "fileName could not be converted to ObjectId"))
        )(any())
      }

      "chunks deletion fails" in {

        val fileController = new FileController (
          mockRasFileRepository,
          mockRasChunksRepository,
          mockMetrics,
          mockAuditService,
          mockAuthConnector,
          mockCC,
          ExecutionContext.global
        ){
          override def getFile(name: String, userId: String): Future[Option[FileData]] = Future(Some(fileData))
        }

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
        when(mockRasChunksRepository.removeChunk(any())).thenReturn(Future.successful(false))
        val fileName = "testFile.csv"
        val result = await(fileController.remove(fileName,"5b4628e02f00002501139c8c").apply(FakeRequest(Helpers.DELETE, s"/ras-api/file/remove/:$fileName")))
        result.header.status shouldBe Status.OK
      }
    }

    "internalservererror" when {
      "when a file doesnt exist" in {
        val fileController = new FileController (
          mockRasFileRepository,
          mockRasChunksRepository,
          mockMetrics,
          mockAuditService,
          mockAuthConnector,
          mockCC,
          ExecutionContext.global
        )

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
        when(mockRasFileRepository.removeFile(any(), any())).thenReturn(Future.successful(false))

        val fileName = "testFile.csv"
        val result = await(fileController.remove(fileName,"5b4628e02f00002501139c8c").apply(FakeRequest(Helpers.DELETE, s"/ras-api/file/remove/:$fileName")))
        result.header.status shouldBe Status.INTERNAL_SERVER_ERROR
      }
    }

    "not remove file and return status 401 (Unauthorised)" when {
      "a remove request has been submitted with no PSA or PP enrolments" in {

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(Future.failed(new InsufficientEnrolments))

        val fileName = "testFile.csv"
        val result = fileController.remove(fileName, "5b4628e02f00002501139c8c").apply(FakeRequest(Helpers.DELETE, s"/ras-api/file/remove/:$fileName"))
        status(result) shouldBe UNAUTHORIZED
      }
    }

    "not remove file return 401 (Unauthorised) for no auth session" when {
      "a remove request has been submitted with no PSA or PP enrolments" in {

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(),any())).thenReturn(Future.failed(new SessionRecordNotFound))

        val fileName = "testFile.csv"
        val result = fileController.remove(fileName,"5b4628e02f00002501139c8c").apply(FakeRequest(Helpers.DELETE, s"/ras-api/file/remove/:$fileName"))
        status(result) shouldBe UNAUTHORIZED
      }
    }
  }
}
