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
import uk.gov.hmrc.rasapi.models.ResidencyStatusFormats.given

class ResidencyStatusResponseSpec extends AnyWordSpec with Matchers {

  "ResidencyStatusSuccess formats" should {

    val fullModel = ResidencyStatusSuccess(
      nino = "AB123456C",
      deathDate = Some("2020-01-01"),
      deathDateStatus = Some("ACTIVE"),
      deseasedIndicator = Some(false),
      currentYearResidencyStatus = "Uk",
      nextYearResidencyStatus = Some("Scottish")
    )

    val fullJson = Json.parse(
      """
        |{
        |  "nino": "AB123456C",
        |  "deathDate": "2020-01-01",
        |  "deathDateStatus": "ACTIVE",
        |  "deseasedIndicator": false,
        |  "currentYearResidencyStatus": "Uk",
        |  "nextYearResidencyStatus": "Scottish"
        |}
        |""".stripMargin
    )

    "serialise all fields when the optional values are present" in {
      Json.toJson(fullModel) shouldBe fullJson
    }

    "deserialise all fields when the optional values are present" in {
      fullJson.as[ResidencyStatusSuccess] shouldBe fullModel
    }

    "omit the optional fields when serialising a model with None values" in {
      val model = ResidencyStatusSuccess(
        nino = "AB123456C",
        deathDate = None,
        deathDateStatus = None,
        deseasedIndicator = None,
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = None
      )

      Json.toJson(model) shouldBe Json.obj(
        "nino"                       -> "AB123456C",
        "currentYearResidencyStatus" -> "Uk"
      )
    }

    "default the optional fields to None when they are absent from the JSON" in {
      val json = Json.obj(
        "nino"                       -> "AB123456C",
        "currentYearResidencyStatus" -> "Uk"
      )

      json.as[ResidencyStatusSuccess] shouldBe ResidencyStatusSuccess(
        nino = "AB123456C",
        deathDate = None,
        deathDateStatus = None,
        deseasedIndicator = None,
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = None
      )
    }
  }

  "ResidencyStatusFailure formats" should {

    val model = ResidencyStatusFailure("STATUS_UNAVAILABLE", "Cannot provide a residency status for this member.")
    val json  = Json.obj(
      "code"   -> "STATUS_UNAVAILABLE",
      "reason" -> "Cannot provide a residency status for this member."
    )

    "serialise to JSON" in {
      Json.toJson(model) shouldBe json
    }

    "deserialise from JSON" in {
      json.as[ResidencyStatusFailure] shouldBe model
    }
  }

  "ResidencyStatusResponse" should {

    "hold a success and no failure" in {
      val success  = ResidencyStatusSuccess("AB123456C", None, None, None, "Uk", None)
      val response = ResidencyStatusResponse(success = Some(success), failure = None)

      response.success shouldBe Some(success)
      response.failure shouldBe None
    }

    "hold a failure and no success" in {
      val failure  = ResidencyStatusFailure("STATUS_UNAVAILABLE", "")
      val response = ResidencyStatusResponse(success = None, failure = Some(failure))

      response.success shouldBe None
      response.failure shouldBe Some(failure)
    }
  }

}
