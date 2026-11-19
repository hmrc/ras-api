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
package uk.gov.hmrc.rasapi.models

import com.fasterxml.jackson.core.JsonProcessingException
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsPath, Json, JsonValidationError, Reads}
import uk.gov.hmrc.http.GatewayTimeoutException

class ConnectorFailureSpec extends AnyWordSpec with Matchers {

  private val cause = new GatewayTimeoutException("GET of 'http://localhost/residency-status' timed out")

  "getMessage" should {

    "be just the reason when there is no status or cause" in {
      ConnectorFailure(FailureReason.TIMEOUT).getMessage shouldBe "TIMEOUT"
    }

    "include the status when there is one" in {
      ConnectorFailure(FailureReason.MATCHING_FAILED, status = Some(403)).getMessage shouldBe
        "MATCHING_FAILED status=403"
    }

    "include the cause's type, but not its message, when there is a cause" in {
      ConnectorFailure(FailureReason.TIMEOUT, cause = Some(cause)).getMessage shouldBe
        "TIMEOUT cause=GatewayTimeoutException"
    }

    "include status and cause together, in that order" in {
      ConnectorFailure(FailureReason.PARSE_ERROR, status = Some(200), cause = Some(cause)).getMessage shouldBe
        "PARSE_ERROR status=200 cause=GatewayTimeoutException"
    }

    "not include parseErrors" in {
      ConnectorFailure(
        FailureReason.INVALID_RESPONSE,
        status = Some(200),
        parseErrors = Some("/nino: error.path.missing")
      ).getMessage shouldBe "INVALID_RESPONSE status=200"
    }

    "be what toString uses, so a stray ConnectorFailure is logged safely" in {
      ConnectorFailure(FailureReason.MATCHING_FAILED, status = Some(403)).toString shouldBe
        "uk.gov.hmrc.rasapi.models.ConnectorFailure: MATCHING_FAILED status=403"
    }
  }

  "a ConnectorFailure" should {
    "not capture a stack trace" in {
      ConnectorFailure(FailureReason.TIMEOUT).getStackTrace shouldBe empty
    }
  }

  "invalidResponse" should {

    "build an INVALID_RESPONSE failure with the status and the formatted JSON paths" in {
      val errors = Seq((JsPath \ "nino") -> Seq(JsonValidationError("error.path.missing")))

      ConnectorFailure.invalidResponse(200, errors) shouldBe ConnectorFailure(
        FailureReason.INVALID_RESPONSE,
        status = Some(200),
        parseErrors = Some("/nino: error.path.missing")
      )
    }

    "join errors for different paths with '; '" in {
      val errors = Seq(
        (JsPath \ "nino")     -> Seq(JsonValidationError("error.path.missing")),
        (JsPath \ "lastName") -> Seq(JsonValidationError("error.expected.jsstring"))
      )

      ConnectorFailure.invalidResponse(200, errors).parseErrors shouldBe
        Some("/nino: error.path.missing; /lastName: error.expected.jsstring")
    }

    "join several messages for the same path with ','" in {
      val errors = Seq(
        (JsPath \ "nino") -> Seq(JsonValidationError("error.minLength"), JsonValidationError("error.pattern"))
      )

      ConnectorFailure.invalidResponse(200, errors).parseErrors shouldBe
        Some("/nino: error.minLength,error.pattern")
    }

    "include JSON paths and error keys only, never the values from the body" in {
      import ConnectorFailureSpec.Member

      val body   = Json.obj("nino" -> "AB123456C", "age" -> "sixty-two")
      val errors = body.validate[Member].asInstanceOf[JsError].errors

      val parseErrors = ConnectorFailure.invalidResponse(200, errors).parseErrors.getOrElse("")

      parseErrors shouldBe "/age: error.expected.jsnumber"
      parseErrors   should not include "AB123456C"
      parseErrors   should not include "sixty-two"
    }
  }

  "parseError" should {

    "build a PARSE_ERROR failure with the status and cause, and no parseErrors" in {
      val exception = intercept[JsonProcessingException](Json.parse("<html>not json</html>"))

      ConnectorFailure.parseError(200, exception) shouldBe ConnectorFailure(
        FailureReason.PARSE_ERROR,
        status = Some(200),
        cause = Some(exception)
      )
    }

    "keep nothing from the parser's message, which can quote the body" in {
      val exception = intercept[JsonProcessingException](Json.parse("""{"nino": AB123456C}"""))

      ConnectorFailure.parseError(200, exception).getMessage shouldBe
        s"PARSE_ERROR status=200 cause=${exception.getClass.getSimpleName}"
    }
  }

}

object ConnectorFailureSpec {

  final case class Member(nino: String, age: Int)

  object Member {
    given Reads[Member] = Json.reads[Member]
  }

}
