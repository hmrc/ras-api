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

package uk.gov.hmrc.rasapi.controllers

import play.api.Logging
import play.api.libs.json.{JsError, JsSuccess, Json}
import play.api.mvc.{Action, AnyContent, ControllerComponents, Result}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.authorisedEnrolments
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.rasapi.models.CreateFileSessionRequest
import uk.gov.hmrc.rasapi.services.RasFilesSessionService

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class FileSessionController @Inject()(val filesSessionService: RasFilesSessionService,
                                      val authConnector: AuthConnector,
                                       cc: ControllerComponents)
                                     (implicit val ec: ExecutionContext) extends BackendController(cc) with AuthorisedFunctions with Logging {

  def createFileSession(): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised().flatMap {
        case Right(_) =>
        request.body.asJson match {
          case Some(json) => Try(json.validate[CreateFileSessionRequest]) match {
            case Success(JsSuccess(payload, _)) =>
              filesSessionService.createFileSession(payload.userId, payload.reference).flatMap {
                case true => Future.successful(Created)
                case false =>
                  logger.warn(s"[FileSessionController][createFileSession] Could not create FileSession. Json Data: $json")
                  Future.successful(InternalServerError("[FileSessionController][createFileSession] Could not create FileSession"))
              }
            case Success(JsError(errors)) =>
              logger.warn(s"[FileSessionController][createFileSession] Json could not be parsed. Json Data: $json, errors: $errors")
              Future.successful(BadRequest(s"Json could not be parsed. Errors: $errors"))
            case Failure(exception) =>
              logger.warn(s"[FileSessionController][createFileSession] Json could not be parsed. Json Data: $json, exception: $exception")
              Future.successful(BadRequest(s"Json could not be parsed. Exception: ${exception.getMessage}"))
          }
          case None =>
            logger.warn(s"[FileSessionController][createFileSession] Missing or invalid json body.")
            Future.successful(BadRequest("Missing or invalid json body"))
        }
        case Left(resp) =>
          logger.info("[FileSessionController][createFileSession] user not authorised")
          Future.successful(resp)
      }
  }

  def fetchFileSession(userId: String): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised().flatMap {
        case Right(uid) if uid == userId => filesSessionService.fetchFileSession(userId).flatMap {
          case Some(fileSession) => Future.successful(Ok(Json.toJson(fileSession)))
          case None =>
            logger.warn(s"[FileSessionController][fetchFileSession] Could not find file session for user: $userId")
            Future.successful(NotFound(s"Could not find file session for user: $userId"))
        }
        case Right(uid) =>
          logger.warn(s"[FileSessionController][fetchFileSession] Enrolled userId: '$uid' does not match the userId in the request: $userId")
          Future.successful(BadRequest(s"Enrolled userId: '$uid' does not match the userId in the request: $userId"))
        case Left(resp) =>
          logger.info("[FileSessionController][fetchFileSession] user not authorised")
          Future.successful(resp)
      }
  }

  def deleteFileSession(userId: String): Action[AnyContent] = Action.async {
    implicit request =>
      isAuthorised().flatMap {
        case Right(uid) if uid == userId => filesSessionService.removeFileSession(userId).flatMap {
          case true => Future.successful(NoContent)
          case false =>
            logger.warn(s"[FileSessionController][deleteFileSession] Delete operation failed for userId: $userId")
            Future.successful(InternalServerError("Delete operation failed"))
        }
        case Right(uid) =>
          logger.warn(s"[FileSessionController][deleteFileSession] Enrolled userId: '$uid' does not match the userId in the request: $userId")
          Future.successful(BadRequest(s"Enrolled userId: '$uid' does not match the userId in the request: $userId"))
        case Left(resp) =>
          logger.info("[FileSessionController][deleteFileSession] user not authorised")
          Future.successful(resp)
      }
  }

  def isAuthorised()(implicit hc: HeaderCarrier, ec: ExecutionContext): Future[Either[Result, String]] = {
    authorised(AuthProviders(GovernmentGateway) and (Enrolment("HMRC-PSA-ORG") or Enrolment("HMRC-PP-ORG") or Enrolment("HMRC-PODS-ORG") or Enrolment("HMRC-PODSPP-ORG"))
    ).retrieve(authorisedEnrolments) {
      enrolments =>
        Future(Right(enrolments.enrolments.head.identifiers.head.value))
    } recover {
      case ex: AuthorisationException => {
        logger.warn(s"[FileSessionController][isAuthorised] user not authorised: $ex")
        Left(Unauthorized(ex.getMessage))
      }
    }
  }
}
