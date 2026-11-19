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

class ResidencyStatusSpec extends AnyWordSpec with Matchers {

  "ResidencyStatus.toString" should {

    "render the current year status joined with the next year forecast when the forecast is present" in {
      ResidencyStatus("otherUKResident", Some("scotResident")).toString shouldBe "otherUKResident,scotResident"
    }

    "render only the current year status when the next year forecast is absent" in {
      ResidencyStatus("otherUKResident", None).toString shouldBe "otherUKResident"
    }
  }

  "ResidencyStatus formats" should {

    val fullModel = ResidencyStatus("otherUKResident", Some("scotResident"))
    val fullJson  = Json.obj(
      "currentYearResidencyStatus"      -> "otherUKResident",
      "nextYearForecastResidencyStatus" -> "scotResident"
    )

    "serialise all fields when the forecast is present" in {
      Json.toJson(fullModel) shouldBe fullJson
    }

    "deserialise all fields when the forecast is present" in {
      fullJson.as[ResidencyStatus] shouldBe fullModel
    }

    "omit the forecast when serialising a model with no next year forecast" in {
      Json.toJson(ResidencyStatus("otherUKResident", None)) shouldBe Json.obj(
        "currentYearResidencyStatus" -> "otherUKResident"
      )
    }

    "default the forecast to None when it is absent from the JSON" in {
      Json.obj("currentYearResidencyStatus" -> "otherUKResident").as[ResidencyStatus] shouldBe
        ResidencyStatus("otherUKResident", None)
    }
  }

}
