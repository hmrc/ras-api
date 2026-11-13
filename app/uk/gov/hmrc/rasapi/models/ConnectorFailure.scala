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

import play.api.libs.json.{JsPath, JsonValidationError}
import uk.gov.hmrc.rasapi.models.FailureReason.{INVALID_RESPONSE, PARSE_ERROR}

import scala.util.control.NoStackTrace

final case class ConnectorFailure(
  reason: FailureReason,
  status: Option[Int] = None,
  parseErrors: Option[String] = None,
  cause: Option[Throwable] = None,
  detail: Option[String] = None, // non-sensitive extra context, e.g. the code from a DES failure body
  upstreamFailure: Option[ResidencyStatusFailure] =
    None // failure body returned by DES, passed back to the caller as-is
) extends Exception with NoStackTrace {

  override def getMessage: String = {
    val statusText = status.map(s => s"status=$s").getOrElse("")
    val causeText  = cause.map(c => s"cause=${c.getClass.getSimpleName}").getOrElse("")

    s"$reason $statusText $causeText".replaceAll("\\s+", " ").trim
  }

}

object ConnectorFailure {

  // when body is JSON, but not in expected shape
  def invalidResponse(
    status: Int,
    errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]
  ): ConnectorFailure =
    ConnectorFailure(INVALID_RESPONSE, status = Some(status), parseErrors = Some(formatJsErrors(errors)))

  // when response body was not JSON
  def parseError(status: Int, exception: Throwable): ConnectorFailure =
    ConnectorFailure(PARSE_ERROR, status = Some(status), cause = Some(exception))

  // play-json validation errors as "path: message" pairs
  def formatJsErrors(errors: collection.Seq[(JsPath, collection.Seq[JsonValidationError])]): String =
    errors
      .map((jsPath, validationErrors) => s"$jsPath: ${validationErrors.flatMap(_.messages).mkString(",")}")
      .mkString("; ")

}
