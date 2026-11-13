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

package uk.gov.hmrc.rasapi.connectors

import play.api.Logging
import play.api.libs.json.{JsError, JsObject, JsSuccess, Json}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.{HeaderCarrier, *}
import uk.gov.hmrc.play.bootstrap.http.HttpClientV2Provider
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.models.*
import uk.gov.hmrc.rasapi.models.FailureReason.*
import uk.gov.hmrc.rasapi.services.{AuditService, DesAuditData}
import uk.gov.hmrc.rasapi.utils.CorrelationIdGenerator
import uk.gov.hmrc.rasapi.utils.LogHelper.failureText

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class DesConnector @Inject() (
  httpPost: HttpClientV2Provider,
  val auditService: AuditService,
  val appContext: AppContext,
  correlationIdGenerator: CorrelationIdGenerator
)(using ec: ExecutionContext)
    extends Logging {

  val uk       = "Uk"
  val scot     = "Scottish"
  val scotRes  = "scotResident"
  val welsh    = "Welsh"
  val welshRes = "welshResident"
  val otherUk  = "otherUKResident"

  val DES_AUDIT_TYPE: String = "ReliefAtSourceDesResidencyStatus"

  private val tag = "[DesConnector][getResidencyStatus]"

  lazy val desBaseUrl: String                = appContext.servicesConfig.baseUrl("des")
  lazy val error_InternalServerError: String = appContext.internalServerErrorStatus
  lazy val error_Deceased: String            = appContext.deceasedStatus
  lazy val error_MatchingFailed: String      = appContext.matchingFailedStatus
  lazy val error_DoNotReProcess: String      = appContext.doNotReProcessStatus
  lazy val error_ServiceUnavailable: String  = appContext.serviceUnavailableStatus
  lazy val allowNoNextYearStatus: Boolean    = appContext.allowNoNextYearStatus
  lazy val retryLimit: Int                   = appContext.requestRetryLimit
  lazy val desUrlHeaderEnv: String           = appContext.desUrlHeaderEnv
  lazy val desAuthToken: String              = appContext.desAuthToken
  lazy val retryDelay: Int                   = appContext.retryDelay
  lazy val isRetryEnabled: Boolean           = appContext.retryEnabled
  lazy val isBulkRetryEnabled: Boolean       = appContext.bulkRetryEnabled
  lazy val error_TooManyRequests: String     = appContext.tooManyRequestsStatus

  lazy val nonRetryableErrors: Seq[String] = List(error_MatchingFailed, error_Deceased, error_DoNotReProcess)

  def canRetryRequest(isBulkRequest: Boolean): Boolean = isRetryEnabled || (isBulkRetryEnabled && isBulkRequest)

  def isCodeRetryable(code: String, isBulkRequest: Boolean): Boolean =
    canRetryRequest(isBulkRequest) && !nonRetryableErrors.contains(code)

  def getResidencyStatus(
    member: IndividualDetails,
    userId: String,
    isBulkRequest: Boolean = false
  )(using hc: HeaderCarrier): Future[Either[ResidencyStatusFailure, ResidencyStatus]] = {

    val uri    = s"$desBaseUrl/individuals/residency-status/"
    val corrId = correlationIdGenerator.correlationId

    val desHeaders = Seq(
      "Environment"   -> desUrlHeaderEnv,
      "OriginatorId"  -> "DA_RAS",
      "Content-Type"  -> "application/json",
      "authorization" -> s"Bearer $desAuthToken",
      "CorrelationId" -> corrId
    )

    def getResultAndProcess(retryCount: Int = 1): Future[Either[ResidencyStatusFailure, ResidencyStatus]] = {
      val requestData = DesRequestData(userId, member.nino, corrId, retryCount, isBulkRequest)

      if (retryCount > 1) {
        logger.warn(s"$tag Retrying DES call userId=$userId corrId=$corrId attempt=$retryCount")
      }

      sendResidencyStatusRequest(uri, member, desHeaders, requestData) flatMap {
        case Right(result)                                                                          => Future.successful(Right(result))
        case Left(result) if retryCount < retryLimit && isCodeRetryable(result.code, isBulkRequest) =>
          Thread.sleep(retryDelay) // Delay before sending to HoD to try to avoid transactions per second(tps) clash
          getResultAndProcess(retryCount + 1)
        case Left(result)                                                                           =>
          Future.successful(
            Left(result.copy(code = result.code.replace(error_DoNotReProcess, error_InternalServerError)))
          )
      }
    }

    getResultAndProcess()
  }

  private def sendResidencyStatusRequest(
    uri: String,
    member: IndividualDetails,
    desHeaders: Seq[(String, String)],
    call: DesRequestData
  )(using hc: HeaderCarrier): Future[Either[ResidencyStatusFailure, ResidencyStatus]] = {

    val payload = Json.toJson(
      Json
        .toJson[IndividualDetails](member)
        .as[JsObject] + ("pensionSchemeOrganisationID" -> Json.toJson(call.userId))
    )

    val desHc = HeaderCarrier(requestId = hc.requestId, sessionId = hc.sessionId)

    httpPost
      .get()
      .post(url"$uri")(using desHc)
      .setHeader(desHeaders*)
      .withBody(payload)
      .execute[HttpResponse]
      .map(resolveResponse)
      .recover { case e => Left(handleException(e)) }
      .map(recordRequestAndTransformResult(_, call, uri))
  }

  private def resolveResponse(response: HttpResponse): Either[ConnectorFailure, (ResidencyStatus, Int)] = {
    val status = response.status

    Try(response.json) match {
      case Failure(e)    => Left(ConnectorFailure.parseError(status, e))
      case Success(json) =>
        json.validate[ResidencyStatusSuccess](ResidencyStatusFormats.successFormats) match {
          case JsSuccess(payload, _) if payload.deseasedIndicator.contains(true) =>
            Left(ConnectorFailure(DECEASED, Some(status)))

          case JsSuccess(payload, _) if payload.nextYearResidencyStatus.isEmpty && !allowNoNextYearStatus =>
            Left(ConnectorFailure(MISSING_NEXT_YEAR_STATUS, Some(status)))

          case JsSuccess(payload, _) =>
            Right(
              ResidencyStatus(
                convertResidencyStatus(payload.currentYearResidencyStatus),
                payload.nextYearResidencyStatus.map(convertResidencyStatus)
              ) -> status
            )
          case JsError(errors)       =>
            json.validate[ResidencyStatusFailure](ResidencyStatusFormats.failureFormats).asOpt match {
              // DES sent its own failure body - code is passed through to the caller unchanged
              case Some(desFailure) =>
                Left(ConnectorFailure(DES_FAILURE_RESPONSE, Some(status), upstreamFailure = Some(desFailure)))
              case None             => Left(ConnectorFailure.invalidResponse(status, errors))
            }
        }
    }
  }

  private def handleException(e: Throwable): ConnectorFailure = e match {
    case _: BadRequestException                 => ConnectorFailure(BAD_REQUEST, Some(400))
    case _: NotFoundException                   => ConnectorFailure(MATCHING_FAILED, Some(404))
    case UpstreamErrorResponse(_, 403, _, _)    => ConnectorFailure(MATCHING_FAILED, Some(403))
    case UpstreamErrorResponse(_, 429, _, _)    => ConnectorFailure(TOO_MANY_REQUESTS, Some(429))
    case UpstreamErrorResponse(_, 503, _, _)    => ConnectorFailure(SERVICE_UNAVAILABLE, Some(503))
    case UpstreamErrorResponse(_, 500, _, _)    => ConnectorFailure(INTERNAL_SERVER_ERROR, Some(500))
    case UpstreamErrorResponse(_, status, _, _) => ConnectorFailure(UNEXPECTED_STATUS, Some(status))
    case _: GatewayTimeoutException             => ConnectorFailure(TIMEOUT, cause = Some(e))
    case _: BadGatewayException                 => ConnectorFailure(CONNECTION_FAILURE, cause = Some(e))
    case _                                      => ConnectorFailure(INTERNAL_SERVER_ERROR, cause = Some(e))
  }

  private def toResidencyStatusFailure(failure: ConnectorFailure): ResidencyStatusFailure = {
    val noStatus      = "Cannot provide a residency status for this pension scheme member."
    val internalError = "Internal server error."

    failure.reason match {
      case DES_FAILURE_RESPONSE                             =>
        failure.upstreamFailure.getOrElse(ResidencyStatusFailure(error_InternalServerError, internalError))
      case MATCHING_FAILED                                  => ResidencyStatusFailure(error_MatchingFailed, noStatus)
      case DECEASED                                         => ResidencyStatusFailure(error_Deceased, noStatus)
      case TOO_MANY_REQUESTS                                => ResidencyStatusFailure(error_TooManyRequests, "Too Many Requests.")
      case SERVICE_UNAVAILABLE                              => ResidencyStatusFailure(error_ServiceUnavailable, "Service unavailable")
      case BAD_REQUEST | TIMEOUT | MISSING_NEXT_YEAR_STATUS =>
        ResidencyStatusFailure(error_DoNotReProcess, internalError)
      case _                                                => ResidencyStatusFailure(error_InternalServerError, internalError)
    }
  }

  private def recordRequestAndTransformResult(
    result: Either[ConnectorFailure, (ResidencyStatus, Int)],
    requestData: DesRequestData,
    uri: String
  )(using
    hc: HeaderCarrier
  ): Either[ResidencyStatusFailure, ResidencyStatus] =
    result match {
      case Right((residencyStatus, httpStatus)) =>
        auditService.audit(DES_AUDIT_TYPE, uri, requestData.successAudit(httpStatus))
        Right(residencyStatus)

      case Left(failure) =>
        val response = toResidencyStatusFailure(failure)

        val context = Seq(
          "corrId"       -> requestData.corrId,
          "attempt"      -> requestData.number.toString,
          "responseCode" -> response.code
        )

        val line = failureText(tag, requestData.userId, failure, context = context)

        failure.reason match {
          case MATCHING_FAILED | DECEASED                                     => logger.info(line)
          case TOO_MANY_REQUESTS | SERVICE_UNAVAILABLE | DES_FAILURE_RESPONSE => logger.warn(line)
          case _                                                              => logger.error(line)
        }

        auditService.audit(DES_AUDIT_TYPE, uri, requestData.failureAudit(failure, response.code))
        Left(response)
    }

  private def convertResidencyStatus(residencyStatus: String): String =
    residencyStatus.replace(uk, otherUk).replace(scot, scotRes).replace(welsh, welshRes)

}
