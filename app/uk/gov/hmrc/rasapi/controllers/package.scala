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

package uk.gov.hmrc.rasapi

import play.api.libs.json.{JsValue, Json, Writes}
import uk.gov.hmrc.auth.core.Enrolments

package object controllers {

  implicit val errorValidationWrite = new Writes[ErrorValidation] {
    def writes(e: ErrorValidation): JsValue = Json.obj("code" -> e.errorCode, "message" -> e.message, "path" -> e.path)
  }

  implicit val errorResponseWrites = new Writes[ErrorResponse] {
    def writes(e: ErrorResponse): JsValue = Json.obj("code" -> e.errorCode, "message" -> e.message)
  }

  implicit val errorResponseWithErrorsWrites = new Writes[ErrorResponseWithErrors] {
    def writes(e: ErrorResponseWithErrors): JsValue = Json.obj("code" -> e.errorCode, "message" -> e.message, "errors" -> e.errors)
  }

  // Auth Constants
  val PSA_ENROLMENT = "HMRC-PSA-ORG"
  val PP_ENROLMENT = "HMRC-PP-ORG"

  def getEnrolmentIdentifier(enrols:Enrolments) = {
    enrols.enrolments.filter(res => (res.key == PSA_ENROLMENT || res.key == PP_ENROLMENT)).map(
      res => res.identifiers.head.value).head
  }
}
