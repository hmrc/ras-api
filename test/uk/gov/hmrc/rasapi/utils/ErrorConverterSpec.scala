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

import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsPath, JsonValidationError}
import uk.gov.hmrc.rasapi.controllers.ErrorValidation

class ErrorConverterSpec
    extends AnyWordSpecLike with Matchers with GuiceOneAppPerSuite with BeforeAndAfter with MockitoSugar {

  "Convert method in a " should {
    "convert an invalid data type" in {
      val error  = JsonValidationError("INVALID_DATA_TYPE")
      val errors = Seq((JsPath(), Seq(error)))

      val errorConverter = new ErrorConverter

      errorConverter.convert(errors) shouldBe
        List(ErrorValidation("INVALID_DATA_TYPE", "Invalid data type has been used", Some("")))
    }
    "convert an invalid date" in {
      val error  = JsonValidationError("INVALID_DATE")
      val errors = Seq((JsPath(), Seq(error)))

      val errorConverter = new ErrorConverter

      errorConverter.convert(errors) shouldBe
        List(ErrorValidation("INVALID_DATE", "Date is invalid", Some("")))
    }
    "convert an invalid format" in {
      val errors = Seq((JsPath(), Seq(JsonValidationError("INVALID_FORMAT"))))

      new ErrorConverter().convert(errors) shouldBe
        List(ErrorValidation("INVALID_FORMAT", "Invalid format has been used", Some("")))
    }
    "convert a MISSING_FIELD error" in {
      val errors = Seq((JsPath(), Seq(JsonValidationError("MISSING_FIELD"))))

      new ErrorConverter().convert(errors) shouldBe
        List(ErrorValidation("MISSING_FIELD", "This field is required", Some("")))
    }
    "convert a Play error.path.missing error to MISSING_FIELD" in {
      val errors = Seq((JsPath(), Seq(JsonValidationError("error.path.missing"))))

      new ErrorConverter().convert(errors) shouldBe
        List(ErrorValidation("MISSING_FIELD", "This field is required", Some("")))
    }
    "preserve the JSON path of the error" in {
      val errors = Seq(((JsPath() \ "nino"), Seq(JsonValidationError("INVALID_FORMAT"))))

      new ErrorConverter().convert(errors) shouldBe
        List(ErrorValidation("INVALID_FORMAT", "Invalid format has been used", Some("/nino")))
    }
    "convert multiple errors into multiple ErrorValidations" in {
      val errors = Seq(
        (JsPath() \ "nino", Seq(JsonValidationError("INVALID_FORMAT"))),
        (JsPath() \ "dateOfBirth", Seq(JsonValidationError("INVALID_DATE")))
      )

      new ErrorConverter().convert(errors) shouldBe List(
        ErrorValidation("INVALID_FORMAT", "Invalid format has been used", Some("/nino")),
        ErrorValidation("INVALID_DATE", "Date is invalid", Some("/dateOfBirth"))
      )
    }
    "throw a MatchError for an unrecognised validation message" in {
      val errors = Seq((JsPath(), Seq(JsonValidationError("SOMETHING_UNKNOWN"))))

      a[MatchError] should be thrownBy new ErrorConverter().convert(errors)
    }
  }

}
