/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.Inject
import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, JsonValidationError}
import uk.gov.hmrc.rasapi.controllers.ErrorValidation

class ErrorConverter @Inject()() {

  def convert(error: Seq[(JsPath, Seq[JsonValidationError])]):List[ErrorValidation] = {
    error.map(e => {
      val details = getErrorDetails(e._2.head.message)

      ErrorValidation(
        errorCode = details._1,
        message = details._2,
        path = Some(e._1.toString())
      )
    }).toList
  }

  private def getErrorDetails(key: String):(String, String) = {
    key match {
      case "INVALID_DATA_TYPE" => ("INVALID_DATA_TYPE", "Invalid data type has been used")
      case "INVALID_DATE" => ("INVALID_DATE", "Date is invalid")
      case "INVALID_FORMAT" => ("INVALID_FORMAT", "Invalid format has been used")
      case "error.path.missing" | "MISSING_FIELD" => ("MISSING_FIELD", "This field is required")
      case _ => throw new MatchError("Could not match the JSON Validation error")
    }
  }

}
