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

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.OptionValues
import org.scalatest.matchers.must.Matchers.mustBe
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Logger
import play.api.libs.json.{JsObject, JsValue, Json, Writes}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.mongo.CurrentTimestampSupport
import uk.gov.hmrc.mongo.cache.{CacheItem, DataKey}
import uk.gov.hmrc.mongo.test.PlayMongoRepositorySupport
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.models.{FileMetadata, FileSession, ResultsFileMetaData, UploadDetails, UpscanCallbackData}
import uk.gov.hmrc.rasapi.repository.RasFilesSessionRepository

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class RasFilesSessionServiceSpec
    extends AnyWordSpec
    with GuiceOneAppPerSuite
    with Matchers
    with MockitoSugar
    with OptionValues
    with PlayMongoRepositorySupport[CacheItem]
    with LogCapturing {

  val appContext: AppContext = app.injector.instanceOf[AppContext]

  val repository: RasFilesSessionRepository =
    new RasFilesSessionRepository(mongoComponent, appContext, new CurrentTimestampSupport)(using
      ExecutionContext.global
    )

  val SUT: RasFilesSessionService = new RasFilesSessionService(repository)(using ExecutionContext.global)

  private val serviceLogger: Logger = Logger(classOf[RasFilesSessionService])

  private val downloadUrl: String =
    "https://test-bucket.example.com/upscan/reference?X-Amz-Signature=fake-signature-for-tests"

  private val originalFileName: String = "smith-family-pension-members.csv"

  private def loggedText(logs: List[ILoggingEvent]): List[String] =
    logs.flatMap(event => event.getFormattedMessage :: Option(event.getThrowableProxy).map(_.getMessage).toList)

  private def assertNoSensitiveDataLogged(logs: List[ILoggingEvent]): Unit =
    loggedText(logs).foreach { text =>
      text should not include downloadUrl
      text should not include "X-Amz-Signature"
      text should not include originalFileName
    }

  private def errorWasLogged(logs: List[ILoggingEvent]): Boolean =
    logs.exists(_.getLevel == Level.ERROR)

  "createFileSession" should {
    "create new session and return true" in {
      val result: Boolean = await(SUT.createFileSession("A1234533", "reference"))

      result shouldBe true
    }

    "return false when repository fails" in {
      val sessionCacheRepositoryMock  = mock[RasFilesSessionRepository]
      val SUT: RasFilesSessionService = new RasFilesSessionService(sessionCacheRepositoryMock)(using
        ExecutionContext.global
      )
      when(
        sessionCacheRepositoryMock.put[FileSession](any)(any(), any())(any)
      ).thenReturn(Future.failed(new Exception("failed")))

      val result = await(SUT.createFileSession("A1234533", "reference"))

      result mustBe false
    }

  }

  "fetchFileSession" should {
    "return file session" in {
      val result = await(SUT.fetchFileSession("A1234533"))

      result shouldBe defined
    }

    "return None when exception occured during get file" in {
      val sessionCacheRepositoryMock  = mock[RasFilesSessionRepository]
      val SUT: RasFilesSessionService = new RasFilesSessionService(sessionCacheRepositoryMock)(using
        ExecutionContext.global
      )
      when(sessionCacheRepositoryMock.get[FileSession]("A1234533")(DataKey("fileSession")))
        .thenReturn(Future.failed(new Exception("failed")))
      val result                      = await(SUT.fetchFileSession("A1234533"))

      result shouldBe None
    }

  }

  "removeFileSession" should {
    "remove file session" in {
      val result = await(SUT.removeFileSession("A1234533"))

      result shouldBe true
    }

    "return false when exception occured while remove file" in {
      val sessionCacheRepositoryMock  = mock[RasFilesSessionRepository]
      val SUT: RasFilesSessionService = new RasFilesSessionService(sessionCacheRepositoryMock)(using
        ExecutionContext.global
      )
      when(sessionCacheRepositoryMock.deleteEntity("userId"))
        .thenReturn(Future.failed(new Exception("failed")))
      val result                      = await(SUT.removeFileSession("userId"))

      result shouldBe false
    }
  }

  "updateFileSession" should {
    "update session cache with processing status, without logging the download URL or file name" in new Setup {
      when(sessionCacheRepositoryMock.get[FileSession]("A1234533")(DataKey("fileSession")))
        .thenReturn(Future.successful(Some(rasSession)))
      when(sessionCacheRepositoryMock.put[FileSession]("A1234533")(DataKey("fileSession"), rasSession))
        .thenReturn(Future.successful(cacheItem))

      withCaptureOfLoggingFrom(serviceLogger) { logs =>
        val res: CacheItem =
          await(sessionService.updateFileSession("A1234533", callbackData, Some(resultsFile), Some(fileMetadata)))

        res.id                  shouldBe "A1234533"
        res.data("fileSession") shouldBe json

        assertNoSensitiveDataLogged(logs)
      }
    }

    "throw RuntimeException when something goes wrong when fetching FileSession, without logging the download URL or file name" in new Setup {
      when(sessionCacheRepositoryMock.get[FileSession]("A1234533")(DataKey("fileSession")))
        .thenReturn(Future.failed(new RuntimeException()))

      withCaptureOfLoggingFrom(serviceLogger) { logs =>
        val exception: RuntimeException = intercept[RuntimeException] {
          await(sessionService.updateFileSession("A1234533", callbackData, Some(resultsFile), Some(fileMetadata)))
        }

        exception.getMessage.contains("Error in saving sessionCache") shouldBe true
        exception.getMessage                                            should not include downloadUrl

        errorWasLogged(logs) shouldBe true
        assertNoSensitiveDataLogged(logs)
      }
    }

    "throw RuntimeException when something goes wrong when storing FileSession, without logging the download URL or file name" in new Setup {
      when(sessionCacheRepositoryMock.get[FileSession]("A1234533")(DataKey("fileSession")))
        .thenReturn(Future.successful(Some(rasSession)))
      when(sessionCacheRepositoryMock.put[FileSession]("A1234533")(DataKey("fileSession"), rasSession))
        .thenReturn(Future.failed(new RuntimeException()))

      withCaptureOfLoggingFrom(serviceLogger) { logs =>
        val exception: RuntimeException = intercept[RuntimeException] {
          await(sessionService.updateFileSession("A1234533", callbackData, Some(resultsFile), Some(fileMetadata)))
        }

        exception.getMessage.contains("Error in saving sessionCache") shouldBe true
        exception.getMessage                                            should not include downloadUrl

        errorWasLogged(logs) shouldBe true
        assertNoSensitiveDataLogged(logs)
      }
    }

    "throw RuntimeException when storing FileSession fails and resultsFile is None, without logging the download URL or file name" in new Setup {
      when(sessionCacheRepositoryMock.get[FileSession]("A1234533")(DataKey("fileSession")))
        .thenReturn(Future.successful(Some(rasSession)))
      when(sessionCacheRepositoryMock.put[FileSession](any[String])(any(), any())(any()))
        .thenReturn(Future.failed(new RuntimeException("disk full")))

      withCaptureOfLoggingFrom(serviceLogger) { logs =>
        val exception: RuntimeException = intercept[RuntimeException] {
          await(sessionService.updateFileSession("A1234533", callbackData, None, None))
        }

        exception.getMessage.contains("Error in saving sessionCache") shouldBe true

        errorWasLogged(logs) shouldBe true
        assertNoSensitiveDataLogged(logs)
      }
    }
  }

  trait Setup {

    val callbackData: UpscanCallbackData = UpscanCallbackData(
      reference = "reference",
      downloadUrl = Some(downloadUrl),
      fileStatus = "READY",
      uploadDetails = Some(
        UploadDetails(
          uploadTimestamp = Instant.parse("2026-11-26T10:00:00Z"),
          checksum = "396f101dd52e8b2ace0dcf5ed09b1d1f030e608938510ce46e7a5c7a4e775100",
          fileMimeType = "text/csv",
          fileName = originalFileName,
          size = 1024
        )
      ),
      failureDetails = None
    )

    val resultsFile: ResultsFileMetaData = ResultsFileMetaData("file-id-1", "fileName.csv", 1234L, 123, 1234L)
    val fileMetadata: FileMetadata       = FileMetadata("file-id-1", Some(originalFileName), None)

    val rasSession: FileSession = FileSession(
      Some(callbackData),
      Some(resultsFile),
      "A1234533",
      Some(Instant.now().toEpochMilli),
      Some(fileMetadata)
    )

    val json: JsValue = Json.toJson(rasSession)

    val cacheItem: CacheItem =
      CacheItem("A1234533", Json.toJson(Map("fileSession" -> json)).as[JsObject], Instant.now, Instant.now)

    protected val sessionCacheRepositoryMock: RasFilesSessionRepository = mock[RasFilesSessionRepository]
    protected val sessionService: RasFilesSessionService                = new RasFilesSessionService(sessionCacheRepositoryMock)
  }

}
