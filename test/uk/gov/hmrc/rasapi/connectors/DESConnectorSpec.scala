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

import org.scalatest.{ShouldMatchers, WordSpec}
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.rasapi.models.{CustomerDetails, ResidencyStatus}

class DESConnectorSpec extends WordSpec with OneAppPerSuite with ShouldMatchers{

  "DESConnector" should {
    "return correct residency status" when {

      "customer with nino: AA123456A is passed in" in {
        DESConnector.getResidencyStatus(CustomerDetails("AA123456A", "First Name", "Last Name", "1989-09-29")).map { result =>
          result shouldBe ResidencyStatus("otherUKResident","otherUKResident")
        }
      }

      "customer with nino: BB123456B is passed in" in {
        DESConnector.getResidencyStatus(CustomerDetails("BB123456B", "John", "Smith", "1975-05-25")).map { result =>
          result shouldBe ResidencyStatus("otherUKResident","scotResident")
        }
      }

      "customer with nino: CC123456C is passed in" in {
        DESConnector.getResidencyStatus(CustomerDetails("CC123456C", "Jane", "Doe", "1969-06-09")).map { result =>
          result shouldBe ResidencyStatus("scotResident", "otherUKResident")
        }
      }

      "customer with nino: EE123456E is passed in" in {

        DESConnector.getResidencyStatus(CustomerDetails("EE123456E", "Joe", "Bloggs", "1982-02-17")).map { result =>
          result shouldBe ResidencyStatus("scotResident", "scotResident")
        }
      }

      "customer with nino: DD123456D is passed in" in {
        DESConnector.getResidencyStatus(CustomerDetails("DD123456D", "", "", "")).map { result =>
          result shouldBe None
        }
      }
    }
  }

}
