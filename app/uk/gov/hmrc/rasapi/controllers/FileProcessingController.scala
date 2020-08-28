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

package uk.gov.hmrc.rasapi.controllers

import javax.inject.Inject
import play.api.Logger
import play.api.libs.json.JsSuccess
import play.api.mvc.{Action, AnyContent, ControllerComponents, Request, Result}
import uk.gov.hmrc.play.bootstrap.controller.{BackendController, BaseController}
import uk.gov.hmrc.rasapi.models.{ApiVersion, CallbackData, V1_0, V2_0}
import uk.gov.hmrc.rasapi.services.{FileProcessingService, SessionCacheService}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Success, Try}

class FileProcessingController @Inject()(
                                          val sessionCacheService: SessionCacheService,
                                          val fileProcessingService: FileProcessingService,
                                          cc: ControllerComponents,
                                          implicit val ec: ExecutionContext
                                        ) extends BackendController(cc) {

  val STATUS_AVAILABLE: String = "AVAILABLE"
  val STATUS_ERROR: String = "ERROR"

  def statusCallback(userId: String, version: String): Action[AnyContent] = Action.async {
    implicit request =>
      val optVersion = version match {
        case "1.0" => Some(V1_0)
        case "2.0" => Some(V2_0)
        case _ => None
      }
      (optVersion, withValidJson) match {
        case (Some(apiVersion), Some(callbackData)) =>
          callbackData.status match {
            case STATUS_AVAILABLE =>
              Logger.info(s"[FileProcessingController][statusCallback] Callback request received with status available: file processing " +
                s"started for userId ($userId).")
              if (Try(fileProcessingService.processFile(userId, callbackData, apiVersion)).isFailure) {
                sessionCacheService.updateFileSession(userId, callbackData, None, None)
              }
            case STATUS_ERROR => Logger.error(s"[FileProcessingController][statusCallback] There is a problem with the " +
              s"file for userId ($userId) ERROR (${callbackData.fileId}), the status is: ${callbackData.status} and the reason is: ${callbackData.reason.get}")
              sessionCacheService.updateFileSession(userId, callbackData, None, None)
            case _ => Logger.warn(s"[FileProcessingController][statusCallback] There is a problem with the file (${callbackData.fileId}) for userId ($userId), the status is:" +
              s" ${callbackData.status}")
          }
          Future(Ok(""))
        case (optVer, optData) => handleInvalidRequest(optVer, optData)
      }
  }

  private def handleInvalidRequest(optVersion: Option[ApiVersion], optCallBackData: Option[CallbackData])(implicit request: Request[_]): Future[Result] = {
    (optVersion, optCallBackData) match {
      case (None, _) => Logger.warn("[FileProcessingController][handleInvalidRequest] Unsupported api version supplied")
      case _ => Logger.warn("[FileProcessingController][handleInvalidRequest] Invalid Json supplied")
    }
    Future.successful(BadRequest(""))
  }

  private def withValidJson()(implicit request: Request[AnyContent]): Option[CallbackData] = {
    request.body.asJson match {
      case Some(json) =>
        Try(json.validate[CallbackData]) match {
          case Success(JsSuccess(payload, _)) => Some(payload)
          case _ => Logger.warn(s"[FileProcessingController][withValidJson] Json could not be parsed. Json Data: $json"); None
        }
      case _ => Logger.warn("[FileProcessingController][withValidJson] No json provided."); None
    }
  }
}
