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

package uk.gov.hmrc.rasapi.controllers

import uk.gov.hmrc.rasapi.config.AppContext

sealed abstract class ErrorResponse(
                                       val httpStatusCode: Int,
                                       val  errorCode: String,
                                       val message: String)

sealed abstract class ErrorResponseWithErrors( val httpStatusCode: Int,
                                               val errorCode: String,
                                               val message: String,
                                               val errors: Option[List[ErrorValidation]] = None)

case class ErrorValidation( errorCode: String,
                            message: String,
                            path: Option[String] = None)

case class ErrorBadRequestResponse(errs: List[ErrorValidation]) extends ErrorResponseWithErrors(
  400,
  "BAD_REQUEST",
  "Bad Request",
  errors = Some(errs))

case object BadRequestResponse extends ErrorResponse(
  400,
  "BAD_REQUEST",
  "Bad Request")

case object Unauthorised extends ErrorResponse(
  401,
  "UNAUTHORIZED",
  "Supplied OAuth token not authorised to access data for given tax identifier(s)")

case object InvalidCredentials extends ErrorResponse(
  401,
  "INVALID_CREDENTIALS",
  "Invalid OAuth token supplied for user-restricted or application-restricted resource (including expired token)")

case object IndividualNotFound extends ErrorResponse(
  403,
  AppContext.matchingFailedStatus,
  "Cannot provide a residency status for this pension scheme member.")

case object TooManyRequestsResponse extends ErrorResponse(
  429,
  AppContext.tooManyRequestsStatus,
  "Request could not be sent 429 (Too Many Requests) was sent from the HoD.")

case object ErrorInternalServerError extends
  ErrorResponse(500, "INTERNAL_SERVER_ERROR", "Internal server error")

case object ErrorServiceUnavailable extends
  ErrorResponse(httpStatusCode = 503, errorCode = "SERVER_ERROR", message = "Service unavailable")

case object ErrorNotFound extends ErrorResponse(404, "NOT_FOUND", "Resource Not Found")
