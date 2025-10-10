/*
 * Copyright 2025 HM Revenue & Customs
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

import org.mockito.ArgumentMatchers.{any, eq => Meq}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, status}
import uk.gov.hmrc.rasapi.controllers.fileProcessing.FileProcessingController
import uk.gov.hmrc.rasapi.models.{ResultsFileMetaData, UploadDetails, UpscanCallbackData, V2_0}
import uk.gov.hmrc.rasapi.services.{FileProcessingService, RasFilesSessionService}

import java.time.Instant
import scala.concurrent.ExecutionContext
import scala.util.Random

class FileProcessingControllerSpec extends AnyWordSpecLike with Matchers with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfter {

  val downloadUrl = "/upscan-download/11370e18-6e24-453e-b45a-76d3e32ea33d"
  val fileId = "file-id-1"
  val fileStatus = "READY"
  val reason: Option[String] = None
  val uploadDetails = UploadDetails(uploadTimestamp = Instant.now(), checksum = "1234567890", fileMimeType = "text/csv", fileName = "test.csv", size = 1234)
  val upscanCallbackData: UpscanCallbackData = UpscanCallbackData(reference = "reference", downloadUrl = Some(downloadUrl), fileStatus = fileStatus, uploadDetails = Some(uploadDetails), None)
  val resultsFile: ResultsFileMetaData = ResultsFileMetaData(fileId, "fileName.csv", 1234L, 123, 1234L)
  val userId: String = Random.nextInt(5).toString

  val mockFileProcessingService: FileProcessingService = mock[FileProcessingService]
  val mockSessionCacheService: RasFilesSessionService = mock[RasFilesSessionService]
  val mockCC: ControllerComponents = app.injector.instanceOf[ControllerComponents]

  val SUT = new FileProcessingController(mockSessionCacheService, mockFileProcessingService, mockCC, ExecutionContext.global)

  before {
    reset(mockFileProcessingService)
    reset(mockSessionCacheService)
  }

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .build()

  lazy val fakeRequest = FakeRequest()

  "statusCallback" should {
    "return Ok and interact with FileProcessingService and SessionCacheService" when {
      "an 'READY' status is given and processFile is successful" in {
        when(mockFileProcessingService.processFile(any(), any(), any())(any(), any())).thenReturn(true)
        val result = SUT.statusCallback(userId, version = "2.0").apply(fakeRequest.withJsonBody(Json.toJson(upscanCallbackData)))

        verify(mockFileProcessingService).processFile(Meq(userId), Meq(upscanCallbackData), Meq(V2_0))(any(), any())
        verify(mockSessionCacheService).updateFileSession(Meq(userId), Meq(upscanCallbackData), Meq(None), Meq(None))

        status(result) shouldBe OK
      }
    }

    "return Ok and not interact with FileProcessingService" when {
      "an 'FAILED' status is given" in {
        val fileStatus = "FAILED"

        val result = SUT.statusCallback(userId, version = "2.0").apply(fakeRequest.withJsonBody(Json.toJson(upscanCallbackData.copy(fileStatus = fileStatus))))

        verifyNoInteractions(mockFileProcessingService)
        verify(mockSessionCacheService).updateFileSession(Meq(userId), Meq(upscanCallbackData.copy(fileStatus = fileStatus)), Meq(None), Meq(None))

        status(result) shouldBe OK
      }
    }
  }
}
