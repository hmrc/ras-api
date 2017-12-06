package uk.gov.hmrc.rasapi.controllers

import play.api.Logger
import play.api.libs.json.JsSuccess
import play.api.mvc.{Action, AnyContent, Request}
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.rasapi.models.CallbackData
import uk.gov.hmrc.rasapi.services.{FileProcessingService, RasFileOutputService}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Success, Try}


object FileProcessingController extends FileProcessingController {

  override val fileProcessingService: FileProcessingService = FileProcessingService
  override val fileOutputService: RasFileOutputService = RasFileOutputService
}

trait FileProcessingController extends BaseController {

  val STATUS_AVAILABLE: String = "AVAILABLE"
  val STATUS_ERROR: String = "ERROR"

  val fileProcessingService: FileProcessingService
  val fileOutputService: RasFileOutputService

  def statusCallback(): Action[AnyContent] = Action.async {
    implicit request =>
      withValidJson.fold(Future.successful(BadRequest(""))){ callbackData =>
        callbackData.status match {
          case STATUS_AVAILABLE =>
            fileOutputService.outputResults(envelopeId = callbackData.envelopeId,
                                            results = fileProcessingService.processFile(callbackData.envelopeId,
                                                                                        callbackData.fileId)) //TO LOOK AT, WILL THIS BE KICKED OFF IN A SEPARATE FUTURE?
          case STATUS_ERROR => Logger.error(s"There is a problem with the file (${callbackData.fileId}), the status is:" +
            s" ${callbackData.status} and the reason is: ${callbackData.reason.get}")
          case _ => Logger.error(s"There is a problem with the file (${callbackData.fileId}), the status is:" +
            s" ${callbackData.status}")
        }

        Future(Ok(""))
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