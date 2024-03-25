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

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures.convertScalaFuture
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar.mock
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.http.Status
import play.api.libs.json.{JsObject, Json}
import play.api.mvc.ControllerComponents
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.auth.core.{AuthConnector, Enrolments, InsufficientEnrolments}
import uk.gov.hmrc.rasapi.models.{CreateFileSessionRequest, FileSession}
import uk.gov.hmrc.rasapi.services.RasFilesSessionService

import java.util.UUID
import scala.concurrent.{ExecutionContext, Future}

class FileSessionControllerSpec extends AnyWordSpecLike with TestEnrolments with GuiceOneAppPerSuite with Matchers {

  val successfulRetrieval: Future[Enrolments] = Future.successful(enrolments)
  val failedRetrieval: Future[Enrolments] = Future.failed(new InsufficientEnrolments)
  val mockAuthConnector: AuthConnector = mock[AuthConnector]
  val mockFilesSessionService: RasFilesSessionService = mock[RasFilesSessionService]
  val mockCC: ControllerComponents = app.injector.instanceOf[ControllerComponents]


  val fileSessionController = new FileSessionController(mockFilesSessionService, mockAuthConnector, mockCC)(ExecutionContext.global)

  val createFileSessionRequest: CreateFileSessionRequest = CreateFileSessionRequest("A123456", UUID.randomUUID().toString)
  val fileSession: FileSession = FileSession(None, None, "A123456", Some(DateTime.now().getMillis), None)

  "createFileSession" should {
    "return CREATED if file session successfully stored" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
      when(mockFilesSessionService.createFileSession(any(), any())).thenReturn(Future.successful(true))
      val jsonRequest = Json.toJson(createFileSessionRequest)

      val result = fileSessionController.createFileSession()
        .apply(FakeRequest(Helpers.POST, "/create-file-session")
          .withJsonBody(jsonRequest))
        .futureValue

      result.header.status shouldBe Status.CREATED
    }

    "return INTERNAL_SERVER_ERROR if file was not stored successfully" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
      when(mockFilesSessionService.createFileSession(any(), any())).thenReturn(Future.successful(false))
      val jsonRequest = Json.toJson(createFileSessionRequest)

      val result = fileSessionController.createFileSession()
        .apply(FakeRequest(Helpers.POST, "/create-file-session")
          .withJsonBody(jsonRequest))
        .futureValue

      result.header.status shouldBe Status.INTERNAL_SERVER_ERROR
    }

    "return BAD_REQUEST if userId is missing" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
      val jsonRequest = Json.toJson(createFileSessionRequest).as[JsObject] - "userId"

      val result = fileSessionController.createFileSession()
        .apply(FakeRequest(Helpers.POST, "/create-file-session")
          .withJsonBody(jsonRequest))
        .futureValue

      result.header.status shouldBe Status.BAD_REQUEST
    }

    "return BAD_REQUEST if reference is missing" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
      val jsonRequest = Json.toJson(createFileSessionRequest).as[JsObject] - "reference"

      val result = fileSessionController.createFileSession()
        .apply(FakeRequest(Helpers.POST, "/create-file-session")
          .withJsonBody(jsonRequest))
        .futureValue

      result.header.status shouldBe Status.BAD_REQUEST
    }

    "return BAD_REQUEST if reference is empty" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
      val jsonRequest = Json.toJson(createFileSessionRequest.copy(reference = ""))

      val result = fileSessionController.createFileSession()
        .apply(FakeRequest(Helpers.POST, "/create-file-session")
          .withJsonBody(jsonRequest))
        .futureValue

      result.header.status shouldBe Status.BAD_REQUEST
    }

    "return BAD_REQUEST if userId is empty" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
      val jsonRequest = Json.toJson(createFileSessionRequest.copy(userId = ""))

      val result = fileSessionController.createFileSession()
        .apply(FakeRequest(Helpers.POST, "/create-file-session")
          .withJsonBody(jsonRequest))
        .futureValue

      result.header.status shouldBe Status.BAD_REQUEST
    }

    "return BAD_REQUEST if request body is empty" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

      val result = fileSessionController.createFileSession()
        .apply(FakeRequest(Helpers.POST, "/create-file-session"))
        .futureValue

      result.header.status shouldBe Status.BAD_REQUEST
    }

    "return UNAUTHORIZED if user is not authorised" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(failedRetrieval)

      val result = fileSessionController.createFileSession()
        .apply(FakeRequest(Helpers.POST, "/create-file-session"))
        .futureValue

      result.header.status shouldBe Status.UNAUTHORIZED
    }
  }

  "fetchFileSession" should {
    "return OK if file session was found for user" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
      when(mockFilesSessionService.fetchFileSession(any())).thenReturn(Future.successful(Some(fileSession)))

      val result = fileSessionController.fetchFileSession("A123456")
        .apply(FakeRequest(Helpers.GET, "/get-file-session/:userId"))
        .futureValue

      result.header.status shouldBe Status.OK
    }

    "return NOT_FOUND if file session was NOT found for user" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
      when(mockFilesSessionService.fetchFileSession(any())).thenReturn(Future.successful(None))

      val result = fileSessionController.fetchFileSession("A123456")
        .apply(FakeRequest(Helpers.GET, "/get-file-session/:userId"))
        .futureValue

      result.header.status shouldBe Status.NOT_FOUND
    }

    "return BAD_REQUEST if userId in request is not matching the enrolled userId" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

      val result = fileSessionController.fetchFileSession("A123457") //wrong userId
        .apply(FakeRequest(Helpers.GET, "/get-file-session/:userId"))
        .futureValue

      result.header.status shouldBe Status.BAD_REQUEST
    }

    "return UNAUTHORIZED if user is not authorised" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(failedRetrieval)

      val result = fileSessionController.fetchFileSession("A123456")
        .apply(FakeRequest(Helpers.GET, "/get-file-session/:userId"))
        .futureValue

      result.header.status shouldBe Status.UNAUTHORIZED
    }
  }

  "deleteFileSession" should {
    "return NO_CONTENT if delete was successful" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
      when(mockFilesSessionService.removeFileSession(any())).thenReturn(Future.successful(true))

      val result = fileSessionController.deleteFileSession("A123456")
        .apply(FakeRequest(Helpers.DELETE, "/delete-file-session/:userId"))
        .futureValue

      result.header.status shouldBe Status.NO_CONTENT
    }

    "return INTERNAL_SERVER_ERROR if delete was NOT successful" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
      when(mockFilesSessionService.removeFileSession(any())).thenReturn(Future.successful(false))

      val result = fileSessionController.deleteFileSession("A123456")
        .apply(FakeRequest(Helpers.DELETE, "/delete-file-session/:userId"))
        .futureValue

      result.header.status shouldBe Status.INTERNAL_SERVER_ERROR
    }

    "return BAD_REQUEST if userId in request is not matching the enrolled userId" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

      val result = fileSessionController.deleteFileSession("A123457")  //wrong userId
        .apply(FakeRequest(Helpers.DELETE, "/delete-file-session/:userId"))
        .futureValue

      result.header.status shouldBe Status.BAD_REQUEST
    }

    "return UNAUTHORIZED if user is not authorised" in {
      when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(failedRetrieval)

      val result = fileSessionController.deleteFileSession("A123456")
        .apply(FakeRequest(Helpers.DELETE, "/delete-file-session/:userId"))
        .futureValue

      result.header.status shouldBe Status.UNAUTHORIZED
    }
  }
}
