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

package uk.gov.hmrc.rasapi.services

import org.joda.time.DateTime
import org.mockito.Mockito._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.mockito.MockitoSugar
import play.api.libs.json.{JsObject, JsValue, Json}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.mongo.cache.{CacheItem, DataKey}
import uk.gov.hmrc.rasapi.models.{CallbackData, FileMetadata, FileSession, ResultsFileMetaData}
import uk.gov.hmrc.rasapi.repository.RasFilesSessionRepository

import java.time.Instant
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RasFilesSessionServiceSpec extends AnyWordSpec with Matchers with MockitoSugar {

  "SessionCacheService" should {
    "update session cache with processing status" in new Setup {
      when(sessionCacheRepositoryMock.get[FileSession]("A1234533")(DataKey("fileSession")))
        .thenReturn(Future.successful(Some(rasSession)))
      when(sessionCacheRepositoryMock.put[FileSession]("A1234533")(DataKey("fileSession"), rasSession))
        .thenReturn(Future.successful(cacheItem))

      val res: CacheItem = await(sessionService.updateFileSession("A1234533", callbackData, Some(resultsFile), Some(fileMetadata)))
      res.id shouldBe "A1234533"
      res.data("fileSession") shouldBe json
    }

    "throw RuntimeException when something goes wrong when fetching FileSession" in new Setup {
      when(sessionCacheRepositoryMock.get[FileSession]("A1234533")(DataKey("fileSession")))
        .thenReturn(Future.failed(new RuntimeException()))

      val exception: RuntimeException = intercept[RuntimeException] {
        await(sessionService.updateFileSession("A1234533", callbackData, Some(resultsFile), Some(fileMetadata)))
      }

      exception.getMessage.contains("Error in saving sessionCache") shouldBe true
    }

    "throw RuntimeException when something goes wrong when storing FileSession" in new Setup {
      when(sessionCacheRepositoryMock.get[FileSession]("A1234533")(DataKey("fileSession")))
        .thenReturn(Future.successful(Some(rasSession)))
      when(sessionCacheRepositoryMock.put[FileSession]("A1234533")(DataKey("fileSession"), rasSession))
        .thenReturn(Future.failed(new RuntimeException()))

      val exception: RuntimeException = intercept[RuntimeException] {
        await(sessionService.updateFileSession("A1234533", callbackData, Some(resultsFile), Some(fileMetadata)))
      }

      exception.getMessage.contains("Error in saving sessionCache") shouldBe true
    }
  }

  trait Setup {
    val callbackData: CallbackData = CallbackData("envelopeId-1234", "file-id-1", "AVAILABLE", None)
    val resultsFile: ResultsFileMetaData = ResultsFileMetaData("file-id-1", "fileName.csv", 1234L, 123, 1234L)
    val fileMetadata: FileMetadata = FileMetadata("file-id-1", Some("fileName"), None)
    val rasSession: FileSession = FileSession(Some(callbackData), Some(resultsFile), "A1234533", Some(DateTime.now().getMillis), Some(fileMetadata))
    val json: JsValue = Json.toJson(rasSession)
    val cacheItem: CacheItem = CacheItem("A1234533", Json.toJson(Map("fileSession" -> json)).as[JsObject], Instant.now, Instant.now)

    protected val sessionCacheRepositoryMock: RasFilesSessionRepository = mock[RasFilesSessionRepository]
    protected val sessionService: RasFilesSessionService = new RasFilesSessionService(sessionCacheRepositoryMock)
  }
}
