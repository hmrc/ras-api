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

package uk.gov.hmrc.rasapi.utils

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import uk.gov.hmrc.http.{GatewayTimeoutException, HeaderCarrier, RequestId}
import uk.gov.hmrc.rasapi.models.{ConnectorFailure, FailureReason}

class LogHelperSpec extends AnyWordSpec with Matchers {

  private val tag    = "[TestConnector][testMethod]"
  private val userId = "A123456"
  private val cause  = new GatewayTimeoutException("GET of 'http://localhost/residency-status' timed out")

  private val noRequestId: HeaderCarrier            = HeaderCarrier()
  implicit private val withRequestId: HeaderCarrier = HeaderCarrier(requestId = Some(RequestId("govuk-tax-test")))

  "successText" should {

    "contain the tag, outcome and userId, followed by the details" in {
      LogHelper.successText(tag, userId, "status=200 fileName=results.csv") shouldBe
        s"$tag outcome=SUCCESS userId=$userId status=200 fileName=results.csv requestId=govuk-tax-test"
    }

    "not leave a trailing space when there are no details" in {
      LogHelper.successText(tag, userId) shouldBe
        s"$tag outcome=SUCCESS userId=$userId requestId=govuk-tax-test"
    }
  }

  "failureText" should {

    "contain only the tag, outcome, reason and userId when nothing else is set" in {
      LogHelper.failureText(tag, userId, ConnectorFailure(FailureReason.TOO_MANY_REQUESTS))(noRequestId) shouldBe
        s"$tag outcome=FAILURE reason=TOO_MANY_REQUESTS userId=$userId"
    }

    "put the message straight after the tag" in {
      val failure = ConnectorFailure(FailureReason.MATCHING_FAILED, status = Some(403))

      LogHelper.failureText(tag, userId, failure, "Member not found")(noRequestId) shouldBe
        s"$tag Member not found outcome=FAILURE reason=MATCHING_FAILED userId=$userId status=403"
    }

    "include every field, in order, when everything is set" in {
      val failure = ConnectorFailure(
        FailureReason.INVALID_RESPONSE,
        status = Some(200),
        parseErrors = Some("/nino: error.path.missing"),
        cause = Some(cause)
      )

      LogHelper.failureText(tag, userId, failure, "Response json did not match expected format")(
        withRequestId
      ) shouldBe
        s"$tag Response json did not match expected format outcome=FAILURE reason=INVALID_RESPONSE userId=$userId " +
        "requestId=govuk-tax-test status=200 parseErrors=/nino: error.path.missing " +
        "cause=GatewayTimeoutException"
    }

    "log the cause's type but not its message" in {
      val failure = ConnectorFailure(FailureReason.TIMEOUT, cause = Some(cause))

      LogHelper.failureText(tag, userId, failure)(noRequestId) shouldBe
        s"$tag outcome=FAILURE reason=TIMEOUT userId=$userId cause=GatewayTimeoutException"
    }

    "collapse newlines so the failure stays on one log line" in {
      val failure = ConnectorFailure(FailureReason.INTERNAL_SERVER_ERROR, status = Some(500))

      LogHelper.failureText(tag, userId, failure, "first line\nsecond line")(noRequestId) shouldBe
        s"$tag first line second line outcome=FAILURE reason=INTERNAL_SERVER_ERROR userId=$userId status=500"
    }
  }

}
