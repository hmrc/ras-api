/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.rasapi.helpers

import org.joda.time.DateTime
import org.scalatest.BeforeAndAfter
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerTest
import uk.gov.hmrc.play.test.UnitSpec

class ResidencyYearResolverSpec extends UnitSpec with MockitoSugar with OneAppPerTest with BeforeAndAfter {

  "isBetweenJanAndApril" should {

    "return false" when {

      "the date is on 6th April" in {

        val SUT = new ResidencyYearResolver {
          override def currentDateTime = new DateTime(2017, 4, 6, 0, 0)
        }

        SUT.isBetweenJanAndApril() shouldBe false
      }

      "the date is on 31st December" in {

        val SUT = new ResidencyYearResolver {
          override def currentDateTime = new DateTime(2017, 12, 31, 0, 0)
        }

        SUT.isBetweenJanAndApril() shouldBe false
      }

      "the date is after 6th April but before 31st December" in {

        val SUT = new ResidencyYearResolver {
          override def currentDateTime = new DateTime(2017, 8, 22, 0, 0)
        }

        SUT.isBetweenJanAndApril() shouldBe false
      }
    }

    "return true" when {
      "the date is on 1st January" in {

        val SUT = new ResidencyYearResolver {
          override def currentDateTime = new DateTime(2017, 1, 22, 0, 0)
        }

        SUT.isBetweenJanAndApril() shouldBe true
      }

      "the date is on 5th April" in {

        val SUT = new ResidencyYearResolver {
          override def currentDateTime = new DateTime(2017, 4, 5, 0, 0)
        }

        SUT.isBetweenJanAndApril() shouldBe true
      }

      "the date is between 1st Jan and 5th April" in {

        val SUT = new ResidencyYearResolver {
          override def currentDateTime = new DateTime(2017, 2, 25, 0, 0)
        }

        SUT.isBetweenJanAndApril() shouldBe true
      }
    }
  }
}
