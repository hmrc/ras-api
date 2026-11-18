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
import play.api.http.Status.*
import play.api.libs.json.{JsValue, Json}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.*
import uk.gov.hmrc.play.bootstrap.http.HttpClientV2Provider
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.models.*
import uk.gov.hmrc.rasapi.services.AuditService

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID.randomUUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class HipConnector @Inject() (
  httpPost: HttpClientV2Provider,
  auditService: AuditService,
  appContext: AppContext
)(using ec: ExecutionContext)
    extends Logging {

  val uk       = "Uk"
  val scot     = "Scottish"
  val scotRes  = "scotResident"
  val welsh    = "Welsh"
  val welshRes = "welshResident"
  val otherUk  = "otherUKResident"

  lazy val baseUrl: String                   = appContext.servicesConfig.baseUrl("hip")
  lazy val error_InternalServerError: String = appContext.internalServerErrorStatus
  lazy val error_Deceased: String            = appContext.deceasedStatus
  lazy val error_MatchingFailed: String      = appContext.matchingFailedStatus
  lazy val error_DoNotReProcess: String      = appContext.doNotReProcessStatus
  lazy val error_ServiceUnavailable: String  = appContext.serviceUnavailableStatus
  lazy val allowNoNextYearStatus: Boolean    = appContext.allowNoNextYearStatus
  lazy val retryLimit: Int                   = appContext.requestRetryLimit
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
  ): Future[Either[ResidencyStatus, ResidencyStatusFailure]] = {

    given rasHeaders: HeaderCarrier = HeaderCarrier()

    lazy val integrationPlatformUri: String = s"$baseUrl/paye/person/regional-residency-status"

    lazy val authSecret: String    = appContext.hipAuthToken
    lazy val hipOriginator: String = appContext.hipOriginator

    lazy val hipHeaders = Seq(
      "Authorization"         -> s"Basic $authSecret",
      "Content-Type"          -> "application/json",
      "correlationId"         -> generateNewUUID,
      "gov-uk-originator-id"  -> hipOriginator,
      "X-Receipt-Date"        -> DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS)),
      "X-Transmitting-System" -> "HIP"
    )

    def getResultAndProcess(
      memberDetails: IndividualDetails,
      retryCount: Int = 1
    ): Future[Either[ResidencyStatus, ResidencyStatusFailure]] = {

      if (retryCount > 1) {
        logger.warn(
          s"[ResultsGenerator][getResultAndProcess] Did not receive a result from HIP, retry count: $retryCount for userId ($userId)."
        )
      }

      sendResidencyStatusRequest(integrationPlatformUri, member, userId, hipHeaders) flatMap {
        case Left(result)                                                                              => Future.successful(Left(result))
        case Right(result) if retryCount < retryLimit && isCodeRetryable(result.code, isBulkRequest)   =>
          Thread.sleep(retryDelay) // Delay before sending to HoD to try to avoid transactions per second(tps) clash
          getResultAndProcess(memberDetails, retryCount + 1)
        case Right(result) if retryCount >= retryLimit || !isCodeRetryable(result.code, isBulkRequest) =>
          Future.successful(
            Right(result.copy(code = result.code.replace(error_DoNotReProcess, error_InternalServerError)))
          )
      }
    }

    getResultAndProcess(member)
  }

  def generateNewUUID: String = randomUUID.toString

  private def sendResidencyStatusRequest(
    uri: String,
    member: IndividualDetails,
    userId: String,
    integrationPlatformHeaders: Seq[(String, String)]
  )(using rasHeaders: HeaderCarrier): Future[Either[ResidencyStatus, ResidencyStatusFailure]] = {
    import member.*

    val hipPayload = HipPayload(nino, firstName, lastName, dateOfBirth = dateOfBirth.toString)
    val result     = httpPost
      .get()
      .post(url"$uri")
      .setHeader(integrationPlatformHeaders*)
      .withBody(Json.toJson[HipPayload](hipPayload))
      .execute[HttpResponse]

    /*
        uk.gov.hmrc.http library makes non-2XX responses produce a future failure rather than a success
        Consequently only 2XX responses are mapped to the resolveResponse method
     */
    result
      .map(response => resolveResponse(response, userId, member.nino))
      .recover {
        case _: BadRequestException              =>
          logger.error(
            s"[HipConnector][getResidencyStatus] Bad Request returned from HIP The details sent were not " +
              s"valid. userId ($userId)."
          )
          Right(ResidencyStatusFailure(error_DoNotReProcess, "Internal server error."))
        case _: NotFoundException                =>
          Right(
            ResidencyStatusFailure(
              error_MatchingFailed,
              "Cannot provide a residency status for this pension scheme member."
            )
          )
        case UpstreamErrorResponse(_, 403, _, _) =>
          logger.error(
            s"Cannot provide a residency status for this pension scheme member for userId ($userId)."
          )
          Right(
            ResidencyStatusFailure(
              error_MatchingFailed,
              "Cannot provide a residency status for this pension scheme member."
            )
          )
        case UpstreamErrorResponse(_, 429, _, _) =>
          logger.error(
            s"[HipConnector][getResidencyStatus] Request could not be sent 429 (Too Many Requests) was sent " +
              s"from the HoD. userId ($userId)."
          )
          Right(ResidencyStatusFailure(error_TooManyRequests, "Too Many Requests."))

        case _: GatewayTimeoutException =>
          logger.error(s"[HipConnector][getResidencyStatus] Request has timed out. userId ($userId).")
          Right(ResidencyStatusFailure(error_DoNotReProcess, "Internal server error."))

        case errorResponse: UpstreamErrorResponse =>
          if (errorResponse.statusCode == 503) {
            logger.error(s"[HipConnector][getResidencyStatus] Service unavailable. userId ($userId).")
            Right(ResidencyStatusFailure(error_ServiceUnavailable, "Service unavailable"))
          } else {
            logger.error(
              s"[HipConnector][getResidencyStatus] ${errorResponse.statusCode} was returned from HIP. userId ($userId)."
            )
            Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
          }

        case th: Throwable =>
          logger.error(
            s"[HipConnector][getResidencyStatus] Caught error occurred when calling the HoD. userId ($userId).Exception message: ${th.getMessage}."
          )
          Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
      }
  }

  private def convertResidencyStatus(residencyStatus: String): String =
    residencyStatus.replace(uk, otherUk).replace(scot, scotRes).replace(welsh, welshRes)

  private def resolveResponse(httpResponse: HttpResponse, userId: String, nino: NINO)(using
    hc: HeaderCarrier
  ): Either[ResidencyStatus, ResidencyStatusFailure] =
    httpResponse.status match {
      case OK                  =>
        Try(httpResponse.json.as[ResidencyStatusSuccess](ResidencyStatusFormats.successFormats)) match {
          case Success(payload) =>
            payload.deseasedIndicator match {
              case Some(true) =>
                Right(
                  ResidencyStatusFailure(
                    error_Deceased,
                    "Cannot provide a residency status for this pension scheme member."
                  )
                )
              case _          =>
                if (payload.nextYearResidencyStatus.isEmpty && !allowNoNextYearStatus) {
                  val auditDataMap = Map("userId" -> userId, "nino" -> nino, "nextYearResidencyStatus" -> "NOT_PRESENT")

                  auditService.audit(
                    auditType = "ReliefAtSourceAudit_HIP_Response",
                    path = "PATH_NOT_DEFINED",
                    auditData = auditDataMap
                  )

                  Right(ResidencyStatusFailure(error_DoNotReProcess, "Internal server error."))
                } else {
                  val currentStatus                  = convertResidencyStatus(payload.currentYearResidencyStatus)
                  val nextYearStatus: Option[String] =
                    payload.nextYearResidencyStatus.map(convertResidencyStatus)
                  Left(ResidencyStatus(currentStatus, nextYearStatus))
                }
            }
          case Failure(ex)      => throw InternalServerException(ex.getMessage)
        }
      /*
        We could validate any json returned here
       */
      case BAD_REQUEST         => throw BadRequestException("")
//        resolveFailureResponse(httpResponse, BadRequestException(""))
      case FORBIDDEN           => throw UpstreamErrorResponse("", 403)
//        resolveFailureResponse(httpResponse, UpstreamErrorResponse("", 403))
      case NOT_FOUND           => throw NotFoundException("")
      case TOO_MANY_REQUESTS   => throw UpstreamErrorResponse("", 429)
      case REQUEST_TIMEOUT     => throw RequestTimeoutException("")
      case SERVICE_UNAVAILABLE => throw UpstreamErrorResponse("", 503)
      case _                   => throw InternalServerException("")
    }

  private def resolveFailureResponse(httpResponse: HttpResponse, t: Throwable) =
    Try(httpResponse.json.as[HipBadRequestResponse](HipErrorResponse.hipErrorResponseReads)) match {
      case Success(data) =>
        logger.debug(s"[HipConnector][resolveResponse] HipFailureResponse from HIP :$data")
        throw t
      case Failure(ex)   =>
        logger.error(s"[HipConnector][resolveResponse] Error from HIP :${ex.getMessage}", ex)
        Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
    }

}
