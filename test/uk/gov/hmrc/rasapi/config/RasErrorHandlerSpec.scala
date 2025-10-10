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

package uk.gov.hmrc.rasapi.config


import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerTest
import play.api.Configuration
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.RequestHeader
import play.api.test.FakeRequest
import play.api.test.Helpers._
import uk.gov.hmrc.auth.core.AuthorisationException
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent
import uk.gov.hmrc.rasapi.controllers.ErrorResponse

import scala.concurrent.ExecutionContext

class RasErrorHandlerSpec extends AnyWordSpecLike with Matchers with MockitoSugar with GuiceOneAppPerTest with BeforeAndAfter{

  implicit val ec: ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  lazy val mockConfiguration: Configuration = app.injector.instanceOf[Configuration]
  lazy val mockAuditConnector: AuditConnector = mock[AuditConnector]
  lazy val mockHttpAuditEvent: HttpAuditEvent = mock[HttpAuditEvent]

  lazy val errorHandler = new RasErrorHandler(mockConfiguration, mockAuditConnector, mockHttpAuditEvent, ec)
  lazy val fakeRequest: RequestHeader = FakeRequest("GET", "/some-url")

  implicit val errorResponseWrites: Writes[ErrorResponse] = new Writes[ErrorResponse] {
    def writes(e: ErrorResponse): JsValue = Json.obj("code" -> e.errorCode, "message" -> e.message)
  }

  "RasErrorHandler.onClientError" should {

    "return NotFound with ErrorNotFound JSON for 404" in {
      val result = errorHandler.onClientError(fakeRequest, NOT_FOUND, "Not found")
      status(result) shouldBe NOT_FOUND
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj("code" -> "NOT_FOUND", "message" -> "Resource Not Found")
    }

    "return BadRequest with BadRequestResponse JSON for 400" in {
      val result= errorHandler.onClientError(fakeRequest, BAD_REQUEST, "Bad Request")
      status(result) shouldBe BAD_REQUEST
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj("code" -> "BAD_REQUEST", "message" -> "Bad Request")
    }

    "delegate to super for other status codes" in {
      val result = errorHandler.onClientError(fakeRequest, UNAUTHORIZED, "Unauthorized")
      status(result) shouldBe UNAUTHORIZED
    }
  }

  "RasErrorHandler.onServerError" should {

    "return Unauthorized JSON for 401 errors" in {
      val result = errorHandler.onServerError(fakeRequest, AuthorisationException.fromString("IncorrectNino"))
      status(result) shouldBe UNAUTHORIZED
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj("code" -> "UNAUTHORIZED", "message" -> "Supplied OAuth token not authorised to access data for given tax identifier(s)")
    }

    "return InternalServerError JSON for other exceptions" in {
      val result = errorHandler.onServerError(fakeRequest, new RuntimeException("Error"))
      status(result) shouldBe INTERNAL_SERVER_ERROR
      contentType(result) shouldBe Some("application/json")
      contentAsJson(result) shouldBe Json.obj("code" -> "INTERNAL_SERVER_ERROR", "message" -> "Internal server error")
    }
  }
}
