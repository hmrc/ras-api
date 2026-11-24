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
import uk.gov.hmrc.http.{HeaderCarrier, RequestId}

class CorrelationIdGeneratorSpec extends AnyWordSpec with Matchers {

  private val generator = new CorrelationIdGenerator()

  // A standard 8-4-4-4-12 UUID string (any hex, any version).
  private val UuidRegex = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$"

  private def corrId(requestId: String): String =
    generator.correlationId(using HeaderCarrier(requestId = Some(RequestId(requestId))))

  // The traceable, request-id-derived part is the first four groups ("xxxxxxxx-xxxx-xxxx-xxxx").
  private def prefix(id: String): String = id.take(23)

  "correlationId" when {

    "a requestId contains a valid UUID-shaped prefix" should {

      // "1a2b3c4d-5e6f-7a8b-9c0d" is pure hex, so it survives UUID validation and is preserved.
      val requestId = "1a2b3c4d-5e6f-7a8b-9c0d-eeeeeeeeeeee"

      "generate a valid UUID" in {
        corrId(requestId) should fullyMatch regex UuidRegex
      }

      "preserve the UUID-shaped prefix so the downstream call is traceable to the originating request" in {
        corrId(requestId) should startWith("1a2b3c4d-5e6f-7a8b-9c0d-")
      }

      "generate a unique id on every call" in {
        corrId(requestId) should not be corrId(requestId)
      }

      "keep the same prefix but a fresh suffix for related requests" in {
        prefix(corrId("1a2b3c4d-5e6f-7a8b-9c0d-aaaaaaaaaaaa")) shouldBe
          prefix(corrId("1a2b3c4d-5e6f-7a8b-9c0d-bbbbbbbbbbbb"))
      }
    }

    "a requestId matches the UUID shape but is not valid UUID hex" should {
      "fall back to a fresh random UUID rather than emit an invalid id" in {
        // "dh12"/"fg34"/"ij56" contain non-hex characters, so the candidate fails validation.
        val id = corrId("abcd0000-dh12-fg34-ij56")
        id should fullyMatch regex UuidRegex
        id should not startWith "abcd0000-dh12-fg34-ij56-"
      }
    }

    "the requestId has no UUID-shaped prefix" should {
      "generate a fresh random UUID" in {
        corrId("not-a-uuid") should fullyMatch regex UuidRegex
      }
    }

    "the requestId is absent" should {

      "generate a fresh random UUID" in {
        generator.correlationId(using HeaderCarrier()) should fullyMatch regex UuidRegex
      }

      "generate a unique id on every call" in {
        generator.correlationId(using HeaderCarrier()) should not be generator.correlationId(using HeaderCarrier())
      }
    }
  }

  "generateNewUUID" should {
    "produce a standard 36-character UUID string" in {
      generator.generateNewUUID should fullyMatch regex
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    }
  }

}
