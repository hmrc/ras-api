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

class LookupControllerSpec extends WordSpec with MockitoSugar with ShouldMatchers with OneAppPerSuite {

  "LookupController" should {
    "return status 200 with text hello world" in {
      val result = LookupController.helloWorld().apply(FakeRequest(Helpers.GET, "/"))

      status(result) shouldBe 200
      contentAsString(result) shouldBe "Hello World"
    }
  }
}
