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
import org.joda.time.DateTime
import play.api.Logger
import play.api.data.validation.ValidationError
import play.api.libs.json.Json._
import play.api.libs.json.{JsError, JsPath, JsSuccess, Json, JsonValidationError}
import play.api.mvc.{Action, AnyContent, BodyParser, BodyParsers, ControllerComponents, Request, Result}
import uk.gov.hmrc.api.controllers.{ErrorAcceptHeaderInvalid, HeaderValidator}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals._
import uk.gov.hmrc.http.{BadRequestException, HeaderCarrier}
import uk.gov.hmrc.play.bootstrap.controller.{BackendController, BaseController}
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.connectors.DesConnector
import uk.gov.hmrc.rasapi.helpers.ResidencyYearResolver
import uk.gov.hmrc.rasapi.metrics.Metrics
import uk.gov.hmrc.rasapi.models._
import uk.gov.hmrc.rasapi.services.AuditService
import uk.gov.hmrc.rasapi.utils.ErrorConverter

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class LookupController @Inject()(
                                val desConnector: DesConnector,
                                val metrics: Metrics,
                                val auditService: AuditService,
                                val authConnector: AuthConnector,
                                val residencyYearResolver: ResidencyYearResolver,
                                val appContext: AppContext,
                                val errorConverter: ErrorConverter,
                                cc: ControllerComponents,
                                implicit val ec: ExecutionContext
                                ) extends BackendController(cc) with HeaderValidator with AuthorisedFunctions {

  def getCurrentDate: DateTime = DateTime.now()
  lazy val allowDefaultRUK: Boolean = appContext.allowDefaultRUK
  lazy val STATUS_DECEASED: String = appContext.deceasedStatus
  lazy val STATUS_MATCHING_FAILED: String = appContext.matchingFailedStatus
  lazy val STATUS_TOO_MANY_REQUESTS: String = appContext.tooManyRequestsStatus
  lazy val STATUS_SERVICE_UNAVAILABLE: String = appContext.serviceUnavailableStatus
  lazy val apiV2_0Enabled : Boolean = appContext.apiV2_0Enabled
  override def parser: BodyParser[AnyContent] = BodyParsers.parse.default
  override protected def executionContext: ExecutionContext = ec

  override val validateVersion: String => Boolean = (version: String) => version == "1.0" || (apiV2_0Enabled && version == "2.0")

  implicit class VersionUtil(request: Request[_]) {
    def getVersion: ApiVersion = {
      request.headers.get(ACCEPT).flatMap(accept => matchHeader(accept).map(_.group("version"))
        .collect {
          case "1.0" => V1_0
          case "2.0" => V2_0
        }).getOrElse(throw new BadRequestException(Json.toJson(ErrorAcceptHeaderInvalid).toString))
      // the exception in the else should never be thrown since it wouldn't pass validation otherwise
    }
  }

  def getResidencyStatus(): Action[AnyContent] = validateAccept(acceptHeaderValidationRules).async {
    implicit request =>
      val apiMetrics = metrics.responseTimer.time
      authorised(AuthProviders(GovernmentGateway) and (Enrolment(PSA_ENROLMENT) or Enrolment(PP_ENROLMENT))).retrieve(authorisedEnrolments) {
        enrols =>
          val id = getEnrolmentIdentifier(enrols)

          withValidJson(id,
            (individualDetails) => {
              desConnector.getResidencyStatus(individualDetails, id, request.getVersion).map {
                case Left(residencyStatusResponse) =>
                  val residencyStatus = if (residencyYearResolver.isBetweenJanAndApril())
                                          updateResidencyResponse(residencyStatusResponse)
                                        else
                                          residencyStatusResponse.copy(nextYearForecastResidencyStatus = None)
                  auditResponse(failureReason = None,
                    nino = Some(individualDetails.nino),
                    residencyStatus = Some(residencyStatus),
                    userId = id)
                  Logger.info(s"[LookupController][getResidencyStatus] Residency status returned successfully for userId ($id).")
                  apiMetrics.stop()
                  Ok(toJson(residencyStatus))

                case Right(matchingFailed) =>
                  matchingFailed.code match {
                    case STATUS_DECEASED =>
                      auditResponse(failureReason = Some(STATUS_DECEASED),
                        nino = Some(individualDetails.nino),
                        residencyStatus = None,
                        userId = id)
                      Logger.info(s"[LookupController][getResidencyStatus] Individual is deceased for userId ($id).")
                      metrics.registry.counter(FORBIDDEN.toString)
                      Forbidden(toJson(IndividualNotFound(appContext.matchingFailedStatus)))
                    case STATUS_MATCHING_FAILED =>
                      auditResponse(failureReason = Some("MATCHING_FAILED"),
                        nino = Some(individualDetails.nino),
                        residencyStatus = None,
                        userId = id)
                      Logger.warn(s"[LookupController][getResidencyStatus] Individual not matched for userId ($id).")
                      metrics.registry.counter(FORBIDDEN.toString)
                      Forbidden(toJson(IndividualNotFound(appContext.matchingFailedStatus)))
                    case STATUS_TOO_MANY_REQUESTS =>
                      auditResponse(failureReason = Some(STATUS_TOO_MANY_REQUESTS),
                        nino = Some(individualDetails.nino),
                        residencyStatus = None,
                        userId = id)
                      Logger.error(s"[LookupController][getResidencyStatus] Too Many Requests for userId ($id).")
                      metrics.registry.counter(TOO_MANY_REQUESTS.toString)
                      TooManyRequests(toJson(TooManyRequestsResponse(appContext.tooManyRequestsStatus)))
                    case STATUS_SERVICE_UNAVAILABLE =>
                      auditResponse(failureReason = Some("SERVICE_UNAVAILABLE"),
                        nino = Some(individualDetails.nino),
                        residencyStatus = None,
                        userId = id)
                      Logger.error(s"[LookupController][getResidencyStatus] Service unavailable for userId ($id).")
                      metrics.registry.counter(SERVICE_UNAVAILABLE.toString)
                      ServiceUnavailable(toJson(ErrorServiceUnavailable))
                    case _ =>
                      auditResponse(failureReason = Some(ErrorInternalServerError.errorCode),
                        nino = Some(individualDetails.nino),
                        residencyStatus = None,
                        userId = id)
                      Logger.error(s"[LookupController][getResidencyStatus] Error returned from DES, error code: " +
                        s"${matchingFailed.code} for userId ($id).")
                      metrics.registry.counter(INTERNAL_SERVER_ERROR.toString)
                      InternalServerError(toJson(ErrorInternalServerError))
                  }
              } recover {
                case th: Throwable =>
                  auditResponse(failureReason = Some(ErrorInternalServerError.errorCode),
                    nino = None,
                    residencyStatus = None,
                    userId = id)
                  Logger.error(s"[LookupController][getResidencyStatus] Error occurred for userId ($id), " +
                    s"Exception message: ${th.getMessage}", th)
                   metrics.registry.counter(INTERNAL_SERVER_ERROR.toString)
                  InternalServerError(toJson(ErrorInternalServerError))
              }
            },
            errors => {
              Logger.warn(s"[LookupController][getResidencyStatus] The request body could not be parsed for userId ($id): " + errors.toString())
              Future.successful(BadRequest(toJson(ErrorBadRequestResponse(errorConverter.convert(errors)))))
            }
          )
      } recoverWith{
        case _:InsufficientEnrolments =>
          Logger.warn("[LookupController][getResidencyStatus] Insufficient privileges")
          metrics.registry.counter(UNAUTHORIZED.toString)
          Future.successful(Unauthorized(toJson(Unauthorised)))
        case _:NoActiveSession =>
          Logger.warn("[LookupController][getResidencyStatus] Inactive session")
          metrics.registry.counter(UNAUTHORIZED.toString)
          Future.successful(Unauthorized(toJson(InvalidCredentials)))
        case ex => Logger.error(s"[LookupController][getResidencyStatus] Exception ${ex.getMessage} was thrown from auth", ex)
          Future.successful(InternalServerError(toJson(ErrorInternalServerError)))
      }
  }

  private def updateResidencyResponse(residencyStatus: ResidencyStatus): ResidencyStatus = {

    if (getCurrentDate.isBefore(new DateTime(2018, 4, 6, 0, 0, 0, 0)) && allowDefaultRUK)
      residencyStatus.copy(currentYearResidencyStatus = desConnector.otherUk)
    else
      residencyStatus
  }

  private def withValidJson (userId: String, onSuccess: (IndividualDetails) => Future[Result],
                             invalidCallback: (Seq[(JsPath, Seq[JsonValidationError])]) => Future[Result])
                            (implicit request: Request[AnyContent]): Future[Result] = {

    request.body.asJson match {
      case Some(json) =>
        Try(json.validate[IndividualDetails](IndividualDetails.individualDetailsReads)) match {
          case Success(JsSuccess(payload, _)) => {

            Try(onSuccess(payload)) match {
              case Success(result) => result
              case Failure(ex: Exception) =>
                Logger.error(s"[LookupController][withValidJson] An error occurred in Json payload validation for userId ($userId) ${ex.getMessage}", ex)
                Future.successful(InternalServerError(toJson(ErrorInternalServerError)))
            }
          }
          case Success(JsError(errors)) =>
            Logger.warn(s"[LookupController][withValidJson] Json error in the request body")
            invalidCallback(errors)
          case Failure(e) =>
            Logger.error(s"[LookupController][withValidJson] An error occurred due to ${e.getMessage} returning " +
            s"internal server error for userId ($userId).")
            Future.successful(InternalServerError(toJson(ErrorInternalServerError)))
        }
      case None => Future.successful(BadRequest(toJson(BadRequestResponse)))
    }
  }

  /**
    * Audits the response, if failure reason is None then residencyStatus is Some (sucess) and vice versa (failure).
    * @param failureReason Optional message, present if the journey failed, else not
    * @param nino Optional user identifier, present if the customer-matching-cache call was a success, else not
    * @param residencyStatus Optional status object returned from the HoD, present if the journey succeeded, else not
    * @param userId Identifies the user which made the request
    * @param request Object containing request made by the user
    * @param hc Headers
    */
  private def auditResponse(failureReason: Option[String], nino: Option[String],
                            residencyStatus: Option[ResidencyStatus], userId: String)
                           (implicit request: Request[AnyContent], hc: HeaderCarrier): Unit = {

    val ninoMap: Map[String, String] = nino.map(nino => Map("nino" -> nino)).getOrElse(Map())
    val nextYearStatusMap: Map[String, String] = if (residencyStatus.nonEmpty) residencyStatus.get.nextYearForecastResidencyStatus
                                                    .map(nextYear => Map("NextCYStatus" -> nextYear)).getOrElse(Map())
                                                 else Map()
    val auditDataMap: Map[String, String] = failureReason.map(
      reason => Map("successfulLookup" -> "false", "reason" -> reason)
    ).getOrElse(Map(
        "successfulLookup" -> "true",
        "CYStatus" -> residencyStatus.get.currentYearResidencyStatus
      ) ++ nextYearStatusMap
    )

    auditService.audit(auditType = "ReliefAtSourceResidency",
      path = request.path,
      auditData = auditDataMap ++ Map("userIdentifier" -> userId, "requestSource" -> "API") ++ ninoMap
    )
  }
}
