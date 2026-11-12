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

  val uuid = "123e4567-e89b-42d3-a456-556642440000"

  val generator: CorrelationIdGenerator = new CorrelationIdGenerator {
    override def generateNewUUID: String = uuid
  }

  "correlationId" when {
    "the requestId is present in the headerCarrier" should {
      "pre-append the requestId when it matches the format (8-4-4-4)" in {
        val requestId = "abcd0000-dh12-fg34-ij56"
        generator.correlationId(using HeaderCarrier(requestId = Some(RequestId(requestId)))) shouldBe
          s"$requestId-${uuid.substring(24)}"
      }

      "return a new id when the requestId does not match the format (8-4-4-4)" in {
        val requestId = "1a2b-dh12-fg34-ij56"
        generator.correlationId(using HeaderCarrier(requestId = Some(RequestId(requestId)))) shouldBe uuid
      }
    }

    "the requestId is not present in the headerCarrier" should {
      "return a new id" in {
        generator.correlationId(using HeaderCarrier()) shouldBe uuid
      }
    }
  }

  "generateNewUUID" should {
    "produce a standard 36-character UUID string" in {
      new CorrelationIdGenerator().generateNewUUID should fullyMatch regex
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"
    }
  }

}
