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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class FailureReasonSpec extends AnyWordSpec with Matchers {

  "FailureReason" should {

    "define exactly the expected set of values" in {
      FailureReason.values.map(_.toString).toSet shouldBe Set(
        "MATCHING_FAILED",
        "TOO_MANY_REQUESTS",
        "SERVICE_UNAVAILABLE",
        "TIMEOUT",
        "INVALID_RESPONSE",
        "UNKNOWN_RESIDENCY_STATUS",
        "INTERNAL_SERVER_ERROR",
        "PARSE_ERROR",
        "CONNECTION_FAILURE",
        "BAD_REQUEST",
        "UNEXPECTED_STATUS",
        "DOWNLOAD_INCOMPLETE"
      )
    }

    "have 12 values" in {
      FailureReason.values.length shouldBe 12
    }

    "resolve a value by name with valueOf" in {
      FailureReason.valueOf("TIMEOUT") shouldBe FailureReason.TIMEOUT
    }

    "throw an IllegalArgumentException for an unknown name" in {
      an[IllegalArgumentException] should be thrownBy FailureReason.valueOf("NOT_A_REASON")
    }

    "render each value's toString as its name" in {
      FailureReason.values.foreach { reason =>
        FailureReason.valueOf(reason.toString) shouldBe reason
      }
    }
  }

}
