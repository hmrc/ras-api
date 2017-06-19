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

package uk.gov.hmrc.rasapi.controllers

import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.test.{FakeRequest, Helpers}
import play.api.test.Helpers._
import org.scalatest.{ShouldMatchers, WordSpec}
import play.api.libs.json.Json
import play.mvc.Http.HeaderNames

class LookupControllerSpec extends WordSpec with MockitoSugar with ShouldMatchers with OneAppPerSuite {

  val acceptHeader: (String, String) = (HeaderNames.ACCEPT, "application/vnd.hmrc.1.0+json")

  "LookupController" should {

    "return status 200 with correct residency status json" when {

      "a valid UUID is given" in {

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"

        val expectedJsonResult = Json.parse(
          """
            {
              "currentYearResidencyStatus" : "otherUKResident",
              "nextYearForecastResidencyStatus" : "otherUKResident"
            }
          """.stripMargin)

        val result = LookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader))

        status(result) shouldBe 200
        contentAsJson(result) shouldBe expectedJsonResult
      }
    }

    "return status 403" when {

      "an invalid UUID is given" in {

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc1"

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INVALID_UUID",
            |  "message": "The match has timed out and the UUID is no longer valid. The match (POST to /match) will need to be repeated."
            |}
          """.stripMargin)

        val result = LookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader))

        status(result) shouldBe 403
        contentAsJson(result) shouldBe expectedJsonResult
      }
    }
  }
}
