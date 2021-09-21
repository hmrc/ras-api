/*
 * Copyright 2021 HM Revenue & Customs
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

import play.api.http.Status._
import play.api.libs.json.{Json, Writes}

sealed abstract class ErrorResponse(
                                       val httpStatusCode: Int,
                                       val errorCode: String,
                                       val message: String)

sealed abstract class ErrorResponseWithErrors( val httpStatusCode: Int,
                                               val errorCode: String,
                                               val message: String,
                                               val errors: Option[List[ErrorValidation]] = None)

case class ErrorValidation(errorCode: String,
                            message: String,
                            path: Option[String] = None)

case class ErrorBadRequestResponse(errs: List[ErrorValidation]) extends ErrorResponseWithErrors(
  BAD_REQUEST,
  "BAD_REQUEST",
  "Bad Request",
  errors = Some(errs))

case object BadRequestResponse extends ErrorResponse(
  BAD_REQUEST,
  "BAD_REQUEST",
  "Bad Request")

case object Unauthorised extends ErrorResponse(
  UNAUTHORIZED,
  "UNAUTHORIZED",
  "Supplied OAuth token not authorised to access data for given tax identifier(s)")

case object InvalidCredentials extends ErrorResponse(
  UNAUTHORIZED,
  "INVALID_CREDENTIALS",
  "Invalid OAuth token supplied for user-restricted or application-restricted resource (including expired token)")

case class IndividualNotFound(matchingFailedStatus: String) extends ErrorResponse(
  FORBIDDEN,
  matchingFailedStatus,
  "Cannot provide a residency status for this pension scheme member.")

case class TooManyRequestsResponse(tooManyRequestsStatus: String) extends ErrorResponse(
  TOO_MANY_REQUESTS,
  tooManyRequestsStatus,
  "Request could not be sent 429 (Too Many Requests) was sent from the HoD.")

object TooManyRequestsResponse {
  implicit val writes: Writes[TooManyRequestsResponse] = Json.writes[TooManyRequestsResponse]
}

case object ErrorServiceUnavailable extends
  ErrorResponse(httpStatusCode = SERVICE_UNAVAILABLE, errorCode = "SERVER_ERROR", message = "Service unavailable")

case object ErrorNotFound extends ErrorResponse(NOT_FOUND, "NOT_FOUND", "Resource Not Found")
