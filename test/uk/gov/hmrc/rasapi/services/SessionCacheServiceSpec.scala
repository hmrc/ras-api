/*
 * Copyright 2020 HM Revenue & Customs
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
import org.mockito.Matchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import play.api.libs.json.{Json, Writes}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.{CacheMap, ShortLivedHttpCaching}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.rasapi.models.{CallbackData, FileMetadata, FileSession, ResultsFileMetaData}

import scala.concurrent.Future

class SessionCacheServiceSpec extends UnitSpec with OneServerPerSuite with ScalaFutures with MockitoSugar with BeforeAndAfter {
  implicit val hc: HeaderCarrier = HeaderCarrier()
  val fileId = "file-id-1"
  val fileStatus = "AVAILABLE"
  val originalFileName = "originalFileName"
  val reason: Option[String] = None
  val callbackData = CallbackData("1234", fileId, fileStatus, reason)
  val resultsFile = ResultsFileMetaData(fileId,Some("fileName.csv"),Some(1234L),123,1234L)
  val fileMetadata = FileMetadata(fileId, None, None)
  val rasSession = FileSession(Some(callbackData), Some(resultsFile), "userId", Some(DateTime.now().getMillis), Some(fileMetadata))
  val json = Json.toJson(rasSession)

  val mockSessionCache = mock[ShortLivedHttpCaching]

  object SUT extends SessionCacheService {
    override val sessionCache: ShortLivedHttpCaching = mockSessionCache
    when(sessionCache.fetchAndGetEntry[FileSession] (any(), any(),any())
      (any(),any(), any()))
      .thenReturn(Future.successful(Some(rasSession)))

    when(sessionCache.cache[FileSession] (any(), any(),any(),any())
      (any[Writes[FileSession]], any[HeaderCarrier], any()))
      .thenReturn(Future.successful(CacheMap("sessionValue", Map("1234" -> json))))
  }

  "SessionCacheService" should {
    "update session cache with processing status" in {
      val res = await(SUT.updateFileSession("1234", callbackData, Some(resultsFile), None))
      res.data.get("1234").get shouldBe json
    }
    "throw RuntimeException when something goes wrong when fetching FileSession" in {
      when(mockSessionCache.fetchAndGetEntry[FileSession](any(), any(), any())(any(), any(), any()))
        .thenReturn(Future.failed(new RuntimeException()))
      val exception = intercept[RuntimeException] {
        await(SUT.updateFileSession("1234", callbackData, Some(resultsFile), None))
      }
      exception.getMessage.contains("Error in saving sessionCache") shouldBe true
    }
    "throw RuntimeException when something goes wrong when storing FileSession" in {
      when(mockSessionCache.fetchAndGetEntry[FileSession](any(), any(), any())(any(), any(), any()))
        .thenReturn(Future.successful(Some(rasSession)))
      when(mockSessionCache.cache[FileSession] (any(), any(),any(),any())(any[Writes[FileSession]], any[HeaderCarrier], any()))
        .thenReturn(Future.failed(new RuntimeException()))
      val exception = intercept[RuntimeException] {
        await(SUT.updateFileSession("1234", callbackData, Some(resultsFile), None))
      }
      exception.getMessage.contains("Error in saving sessionCache") shouldBe true
    }
  }
}
