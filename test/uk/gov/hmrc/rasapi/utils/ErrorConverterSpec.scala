/*
 * Copyright 2023 HM Revenue & Customs
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

import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsPath, JsonValidationError}
import uk.gov.hmrc.rasapi.controllers.ErrorValidation

class ErrorConverterSpec extends AnyWordSpecLike with Matchers with GuiceOneAppPerSuite with BeforeAndAfter with MockitoSugar {
  "Convert method in a " should {
    "convert an invalid data type" in {
     val error = JsonValidationError("INVALID_DATA_TYPE")
      val errors = Seq((JsPath(), Seq(error)))

      val errorConverter = new ErrorConverter

      errorConverter.convert(errors) shouldBe
        List(ErrorValidation("INVALID_DATA_TYPE", "Invalid data type has been used", Some("")))
    }
    "convert an invalid date" in {
      val error = JsonValidationError("INVALID_DATE")
      val errors = Seq((JsPath(), Seq(error)))

      val errorConverter = new ErrorConverter

      errorConverter.convert(errors) shouldBe
        List(ErrorValidation("INVALID_DATE", "Date is invalid", Some("")))
    }
  }
}
