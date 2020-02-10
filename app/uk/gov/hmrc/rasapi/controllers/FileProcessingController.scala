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

import play.api.Logger
import play.api.libs.json.JsSuccess
import play.api.mvc.{Action, AnyContent, Request}
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.rasapi.models.{CallbackData, V1_0, V2_0}
import uk.gov.hmrc.rasapi.services.{FileProcessingService, SessionCacheService}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Success, Try}

object FileProcessingController extends FileProcessingController {

  override val fileProcessingService: FileProcessingService = FileProcessingService
  override val sessionCacheService: SessionCacheService = SessionCacheService
}

trait FileProcessingController extends BaseController {

  val STATUS_AVAILABLE: String = "AVAILABLE"
  val STATUS_ERROR: String = "ERROR"

  val fileProcessingService: FileProcessingService
  val sessionCacheService: SessionCacheService

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
              Logger.warn(s"[FileProcessingController] [statusCallback] Callback request received with status available: file processing " +
                s"started for userId ($userId).")
              if (Try(fileProcessingService.processFile(userId, callbackData, apiVersion)).isFailure) {
                sessionCacheService.updateFileSession(userId, callbackData, None, None)
              }
            case STATUS_ERROR => Logger.error(s"[FileProcessingController] [statusCallback] There is a problem with the " +
              s"file for userId ($userId) ERROR (${callbackData.fileId}), the status is: ${callbackData.status} and the reason is: ${callbackData.reason.get}")
              sessionCacheService.updateFileSession(userId, callbackData, None, None)
            case _ => Logger.warn(s"There is a problem with the file (${callbackData.fileId}) for userId ($userId), the status is:" +
              s" ${callbackData.status}")
          }
          Future(Ok(""))
        case _ => Future.successful(BadRequest(""))
      }
  }

  private def withValidJson()(implicit request: Request[AnyContent]): Option[CallbackData] = {
    request.body.asJson match {
      case Some(json) =>
        Try(json.validate[CallbackData]) match {
          case Success(JsSuccess(payload, _)) => Some(payload)
          case _ => Logger.info(s"Json could not be parsed. Json Data: $json"); None
        }
      case _ => Logger.info("No json provided."); None
    }
  }
}