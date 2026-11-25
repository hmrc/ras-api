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

class CorrelationIdGeneratorSpec extends AnyWordSpec with Matchers {

  private val generator = new CorrelationIdGenerator()

  // A standard 8-4-4-4-12 UUID whose version nibble is 4 and variant nibble is one of 8/9/a/b (i.e. a valid v4 UUID).
  private val UuidV4Regex = "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$"

  "correlationId" should {

    "generate a valid v4 UUID" in {
      generator.correlationId should fullyMatch regex UuidV4Regex
    }

    "generate a unique id on every call" in {
      generator.correlationId should not be generator.correlationId
    }
  }

  "generateNewUUID" should {
    "produce a valid v4 UUID" in {
      generator.generateNewUUID should fullyMatch regex UuidV4Regex
    }
  }

}
