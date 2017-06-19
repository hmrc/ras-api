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

package uk.gov.hmrc.rasapi.models

import play.api.libs.json.{JsValue, Json, Writes}

sealed abstract class ErrorResponse(
                                       val httpStatusCode: Int,
                                       val  errorCode: String,
                                       val message: String)

case object InvalidUUIDForbiddenResponse extends ErrorResponse(
  403,
  "INVALID_UUID",
  "The match has timed out and the UUID is no longer valid. " +
    "The match (POST to /match) will need to be repeated.") with JsonFormats

trait JsonFormats {
  implicit val errorResponseWrites = new Writes[ErrorResponse] {
    def writes(e: ErrorResponse): JsValue = Json.obj("code" -> e.errorCode, "message" -> e.message)
  }
}