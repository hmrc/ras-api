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

package uk.gov.hmrc.rasapi.controllers

import play.api.libs.json.{Json, OWrites}
import play.api.mvc._

import scala.concurrent.{ExecutionContext, Future}

final case class ApiErrorResponse(code: String, message: String)

object ApiErrorResponse {
  implicit val writes: OWrites[ApiErrorResponse] = Json.writes[ApiErrorResponse]

  val internalServerError: ApiErrorResponse =
    ApiErrorResponse("INTERNAL_SERVER_ERROR", "Internal server error")

  val acceptHeaderInvalid: ApiErrorResponse =
    ApiErrorResponse("ACCEPT_HEADER_INVALID", "The accept header is missing or invalid")
}

trait SimpleHeaderValidator { self: BaseController =>

  protected def executionContext: ExecutionContext

  def validateVersion(version: String): Boolean = true

  private val AcceptHeaderVersionRegex = "application/vnd\\.hmrc\\.(\\d+\\.\\d+)\\+json".r

  protected def matchesAcceptHeader(accepted: Seq[String])(request: RequestHeader): Boolean =
    request.headers
      .get("Accept")
      .exists { header =>
        accepted.exists(header.contains) &&
          AcceptHeaderVersionRegex
            .findFirstMatchIn(header)
            .exists(m => validateVersion(m.group(1)))
      }

  protected def validateAccept(accepted: Seq[String]): ActionBuilder[Request, AnyContent] =
    new ActionBuilderImpl(controllerComponents.parsers.default)(executionContext) {
      override def invokeBlock[A](
                                   request: Request[A],
                                   block: Request[A] => Future[Result]
                                 ): Future[Result] =
        if (matchesAcceptHeader(accepted)(request)) {
          block(request)
        } else {
          Future.successful(NotAcceptable(Json.toJson(ApiErrorResponse.acceptHeaderInvalid)))
        }
    }
}