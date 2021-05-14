/*
 * Copyright 2021 HM Revenue & Customs
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
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.http.Status.OK
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.json.Json
import play.api.mvc.ControllerComponents
import play.api.test.FakeRequest
import play.api.test.Helpers.{defaultAwaitTimeout, status}
import uk.gov.hmrc.mongo.Awaiting
import uk.gov.hmrc.rasapi.models.{CallbackData, ResultsFileMetaData, V2_0}
import uk.gov.hmrc.rasapi.services.{FileProcessingService, SessionCacheService}

import scala.concurrent.ExecutionContext
import scala.util.Random

class FileProcessingControllerSpec extends WordSpecLike with Matchers with Awaiting with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfter {

  val envelopeId = "0b215ey97-11d4-4006-91db-c067e74fc653"
  val fileId = "file-id-1"
  val fileStatus = "AVAILABLE"
  val reason: Option[String] = None
  val callbackData: CallbackData = CallbackData(envelopeId, fileId, fileStatus, reason)
  val resultsFile: ResultsFileMetaData = ResultsFileMetaData(fileId, Some("fileName.csv"), Some(1234L), 123, 1234L)
  val userId: String = Random.nextInt(5).toString

  val mockFileProcessingService: FileProcessingService = mock[FileProcessingService]
  val mockSessionCacheService: SessionCacheService = mock[SessionCacheService]
  val mockCC: ControllerComponents = app.injector.instanceOf[ControllerComponents]

  val SUT = new FileProcessingController(mockSessionCacheService, mockFileProcessingService, mockCC, ExecutionContext.global)

  before {
    reset(mockFileProcessingService)
    reset(mockSessionCacheService)
  }

  override def fakeApplication(): Application =
    GuiceApplicationBuilder()
      .disable[com.kenshoo.play.metrics.PlayModule]
      .build()

  lazy val fakeRequest = FakeRequest()

  "statusCallback" should {
    "return Ok and interact with FileProcessingService and SessionCacheService" when {
      "an 'AVAILABLE' status is given" in {

        val result = SUT.statusCallback(userId, version = "2.0").apply(fakeRequest.withJsonBody(Json.toJson(callbackData)))

        verify(mockFileProcessingService).processFile(Meq(userId), Meq(callbackData), Meq(V2_0))(any(), any())

        status(result) shouldBe OK
      }
    }

    "return Ok and not interact with FileProcessingServicec" when {
      "an 'ERROR' status is given" in {
        val envelopeId = "0b215ey97-11d4-4006-91db-c067e74fc653"
        val fileId = "file-id-1"
        val fileStatus = "ERROR"
        val reason: Option[String] = Some("VirusDetected")
        val callbackData = CallbackData(envelopeId, fileId, fileStatus, reason)

        val result = SUT.statusCallback(userId, version = "2.0").apply(fakeRequest.withJsonBody(Json.toJson(callbackData)))

        verifyNoInteractions(mockFileProcessingService)
        verify(mockSessionCacheService).updateFileSession(Meq(userId), Meq(callbackData), Meq(None), Meq(None))(any())

        status(result) shouldBe OK
      }

      "a 'QUARANTINED' status is given" in {
        val envelopeId = "0b215ey97-11d4-4006-91db-c067e74fc653"
        val fileId = "file-id-1"
        val fileStatus = "QUARANTINED"
        val reason: Option[String] = None
        val callbackData = CallbackData(envelopeId, fileId, fileStatus, reason)

        val result = SUT.statusCallback(userId, version = "2.0").apply(fakeRequest.withJsonBody(Json.toJson(callbackData)))

        verifyNoInteractions(mockFileProcessingService)
        verifyNoInteractions(mockSessionCacheService)

        status(result) shouldBe OK
      }

      "a 'CLEANED'status is given" in {
        val envelopeId = "0b215ey97-11d4-4006-91db-c067e74fc653"
        val fileId = "file-id-1"
        val fileStatus = "CLEANED"
        val reason: Option[String] = None
        val callbackData = CallbackData(envelopeId, fileId, fileStatus, reason)

        val result = SUT.statusCallback(userId, version = "2.0").apply(fakeRequest.withJsonBody(Json.toJson(callbackData)))

        verifyNoInteractions(mockFileProcessingService)
        verifyNoInteractions(mockSessionCacheService)

        status(result) shouldBe OK
      }

      "an 'INFECTED' status is given" in {
        val envelopeId = "0b215ey97-11d4-4006-91db-c067e74fc653"
        val fileId = "file-id-1"
        val fileStatus = "INFECTED"
        val reason: Option[String] = None
        val callbackData = CallbackData(envelopeId, fileId, fileStatus, reason)

        val result = SUT.statusCallback(userId, version = "2.0").apply(fakeRequest.withJsonBody(Json.toJson(callbackData)))

        verifyNoInteractions(mockFileProcessingService)
        verifyNoInteractions(mockSessionCacheService)

        status(result) shouldBe OK
      }
    }
  }
}