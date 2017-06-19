/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.rasapi.connectors

import org.scalatest.mock.MockitoSugar
import org.scalatest.{WordSpec, _}
import uk.gov.hmrc.rasapi.models.CustomerDetails

class CachingConnectorSpec extends WordSpec with MockitoSugar with ShouldMatchers {

  val SUT = CachingConnector

  "getCachedData" should {
    "return an option of some CustomerDetails" when {
      "a valid uuid (2800a7ab-fe20-42ca-98d7-c33f4133cfc2) is found" in {

        val uuid = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"

        val result = SUT.getCachedData(uuid)

        result shouldBe Some(CustomerDetails("AA123456A", "First Name", "Last Name", "1989-09-29"))
      }

      "a valid uuid (633e0ee7-315b-49e6-baed-d79c3dffe467) is found" in {

        val uuid = "633e0ee7-315b-49e6-baed-d79c3dffe467"

        val result = SUT.getCachedData(uuid)

        result shouldBe Some(CustomerDetails("BB123456B", "John", "Smith", "1975-05-25"))
      }

      "a valid uuid (77648d82-309e-484d-a310-d0ffd2997791) is found" in {

        val uuid = "77648d82-309e-484d-a310-d0ffd2997791"

        val result = SUT.getCachedData(uuid)

        result shouldBe Some(CustomerDetails("CC123456C", "Jane", "Doe", "1969-06-09"))
      }

      "a valid uuid (79f21755-8cd4-4785-9c10-13253f7a8bb6) is found" in {

        val uuid = "79f21755-8cd4-4785-9c10-13253f7a8bb6"

        val result = SUT.getCachedData(uuid)

        result shouldBe Some(CustomerDetails("EE123456E", "Joe", "Bloggs", "1982-02-17"))
      }
    }

    "return an option of none" when {
      "an invalid UUID is given" in {
        val uuid = "2800a7ab-fe20-42ca-98d7-c33f4133cgw4"

        val result = SUT.getCachedData(uuid)

        result shouldBe None
      }
    }
  }
}
