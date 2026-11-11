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
import play.api.libs.json.Json

import java.time.LocalDate

class RawMemberDetailsSpec extends AnyWordSpec with Matchers {

  "RawMemberDetails" should {

    val model = RawMemberDetails("AB123456C", "John", "Doe", "1990-01-02")
    val json  = Json.obj(
      "nino"        -> "AB123456C",
      "firstName"   -> "John",
      "lastName"    -> "Doe",
      "dateOfBirth" -> "1990-01-02"
    )

    "serialise to JSON" in {
      Json.toJson(model) shouldBe json
    }

    "deserialise from JSON" in {
      json.as[RawMemberDetails] shouldBe model
    }

    "default every field to an empty string" in {
      RawMemberDetails() shouldBe RawMemberDetails("", "", "", "")
    }
  }

  "IndividualDetails reads" should {

    "upper case the nino and names and parse an ISO date of birth" in {
      val json = Json.obj(
        "nino"        -> "ab123456c",
        "firstName"   -> "John",
        "lastName"    -> "Doe",
        "dateOfBirth" -> "1990-01-02"
      )

      json.as[IndividualDetails](IndividualDetails.individualDetailsReads) shouldBe
        IndividualDetails("AB123456C", "JOHN", "DOE", LocalDate.of(1990, 1, 2))
    }

    "parse a bulk formatted date of birth" in {
      val json = Json.obj(
        "nino"        -> "AB123456C",
        "firstName"   -> "John",
        "lastName"    -> "Doe",
        "dateOfBirth" -> "02/01/1990"
      )

      json.as[IndividualDetails](IndividualDetails.individualDetailsBulkReads) shouldBe
        IndividualDetails("AB123456C", "JOHN", "DOE", LocalDate.of(1990, 1, 2))
    }
  }

  "IndividualDetails writes" should {

    "render the date of birth under a dob field formatted as yyyy-MM-dd" in {
      val model = IndividualDetails("AB123456C", "JOHN", "DOE", LocalDate.of(1990, 1, 2))

      Json.toJson(model) shouldBe Json.obj(
        "nino"      -> "AB123456C",
        "firstName" -> "JOHN",
        "lastName"  -> "DOE",
        "dob"       -> "1990-01-02"
      )
    }
  }

}
