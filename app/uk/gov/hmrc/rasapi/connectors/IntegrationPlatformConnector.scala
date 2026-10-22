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
import play.api.libs.json.{JsObject, Json}
import uk.gov.hmrc.http.{HeaderCarrier, *}
import uk.gov.hmrc.play.bootstrap.http.HttpClientV2Provider
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.models.*
import uk.gov.hmrc.rasapi.services.AuditService
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID.randomUUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait IntegrationPlatformConnector @Inject()(
                                              httpPost: HttpClientV2Provider,
                                              auditService: AuditService,
                                              appContext: AppContext
                                            )(using ec: ExecutionContext) extends Logging {
  val uk       = "Uk"
  val scot     = "Scottish"
  val scotRes  = "scotResident"
  val welsh    = "Welsh"
  val welshRes = "welshResident"
  val otherUk  = "otherUKResident"

  def baseUrl: String
  def integrationPlatformUri: String

  def generateNewUUID: String = randomUUID.toString

  given rasHeaders: HeaderCarrier = HeaderCarrier()

  def getResidencyStatus(
                          member: IndividualDetails,
                          userId: String,
                          isBulkRequest: Boolean = false
                        ): Future[Either[ResidencyStatus, ResidencyStatusFailure]]

  lazy val error_InternalServerError: String = appContext.internalServerErrorStatus
  lazy val error_Deceased: String            = appContext.deceasedStatus
  lazy val error_MatchingFailed: String      = appContext.matchingFailedStatus
  lazy val error_DoNotReProcess: String      = appContext.doNotReProcessStatus
  lazy val error_ServiceUnavailable: String  = appContext.serviceUnavailableStatus
  lazy val allowNoNextYearStatus: Boolean    = appContext.allowNoNextYearStatus
  lazy val retryLimit: Int                   = appContext.requestRetryLimit
  lazy val desUrlHeaderEnv: String           = appContext.desUrlHeaderEnv

  lazy val retryDelay: Int                   = appContext.retryDelay
  lazy val isRetryEnabled: Boolean           = appContext.retryEnabled
  lazy val isBulkRetryEnabled: Boolean       = appContext.bulkRetryEnabled
  lazy val error_TooManyRequests: String     = appContext.tooManyRequestsStatus

  lazy val nonRetryableErrors: Seq[String] = List(error_MatchingFailed, error_Deceased, error_DoNotReProcess)

  def canRetryRequest(isBulkRequest: Boolean): Boolean = isRetryEnabled || (isBulkRetryEnabled && isBulkRequest)

  def isCodeRetryable(code: String, isBulkRequest: Boolean): Boolean =
    canRetryRequest(isBulkRequest) && !nonRetryableErrors.contains(code)

  protected def getIPResidencyStatus(
    member: IndividualDetails,
    userId: String,
    integrationPlatformHeaders: Seq[(String, String)],
    isBulkRequest: Boolean
  ): Future[Either[ResidencyStatus, ResidencyStatusFailure]] = {

    def getResultAndProcess(
      memberDetails: IndividualDetails,
      retryCount: Int = 1
    ): Future[Either[ResidencyStatus, ResidencyStatusFailure]] = {

      if (retryCount > 1) {
        logger.warn(
          s"[ResultsGenerator][getResultAndProcess] Did not receive a result from des, retry count: $retryCount for userId ($userId)."
        )
      }

      sendResidencyStatusRequest(integrationPlatformUri, member, userId, integrationPlatformHeaders) flatMap {
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

  private def sendResidencyStatusRequest(
    uri: String,
    member: IndividualDetails,
    userId: String,
    desHeaders: Seq[(String, String)]
  )(using rasHeaders: HeaderCarrier): Future[Either[ResidencyStatus, ResidencyStatusFailure]] = {

    val payload = Json.toJson(
      Json
        .toJson[IndividualDetails](member)
        .as[JsObject] + ("pensionSchemeOrganisationID" -> Json.toJson(userId))
    )
    val result  = httpPost
      .get()
      .post(url"$uri")
      .setHeader(desHeaders*)
      .withBody(payload)
      .execute[HttpResponse]
    result.map(response => resolveResponse(response, userId, member.nino)).recover {
      case _: BadRequestException              =>
        logger.error(
          s"[IntegrationPlatformConnector][getResidencyStatus] Bad Request returned from des. The details sent were not " +
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

      case UpstreamErrorResponse(_, 429, _, _)  =>
        logger.error(
          s"[IntegrationPlatformConnector][getResidencyStatus] Request could not be sent 429 (Too Many Requests) was sent " +
            s"from the HoD. userId ($userId)."
        )
        Right(ResidencyStatusFailure(error_TooManyRequests, "Too Many Requests."))
      case _: RequestTimeoutException           =>
        logger.error(s"[IntegrationPlatformConnector][getResidencyStatus] Request has timed out. userId ($userId).")
        Right(ResidencyStatusFailure(error_DoNotReProcess, "Internal server error."))
      case errorResponse: UpstreamErrorResponse =>
        if (errorResponse.statusCode == 503) {
          logger.error(s"[IntegrationPlatformConnector][getResidencyStatus] Service unavailable. userId ($userId).")
          Right(ResidencyStatusFailure(error_ServiceUnavailable, "Service unavailable"))
        } else {
          logger.error(
            s"[IntegrationPlatformConnector][getResidencyStatus] ${errorResponse.statusCode} was returned from DES. userId ($userId)."
          )
          Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
        }
      case th: Throwable                        =>
        logger.error(
          s"[IntegrationPlatformConnector][getResidencyStatus] Caught error occurred when calling the HoD. userId ($userId).Exception message: ${th.getMessage}."
        )
        Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
    }
  }

  private def convertResidencyStatus(residencyStatus: String): String =
    residencyStatus.replace(uk, otherUk).replace(scot, scotRes).replace(welsh, welshRes)

  private def resolveResponse(httpResponse: HttpResponse, userId: String, nino: NINO)(using
    hc: HeaderCarrier
  ): Either[ResidencyStatus, ResidencyStatusFailure] =

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
                auditType = "ReliefAtSourceAudit_DES_Response",
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
      case Failure(_)       =>
        Try(httpResponse.json.as[ResidencyStatusFailure](ResidencyStatusFormats.failureFormats)) match {
          case Success(data) =>
            logger.debug(s"[IntegrationPlatformConnector][resolveResponse] DesFailureResponse from DES :$data")
            Right(data)
          case Failure(ex)   =>
            logger.error(s"[IntegrationPlatformConnector][resolveResponse] Error from DES :${ex.getMessage}", ex)
            Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
        }
    }

}

object IntegrationPlatformConnector {
  val uk = "Uk"
  val scot = "Scottish"
  val scotRes = "scotResident"
  val welsh = "Welsh"
  val welshRes = "welshResident"
  val otherUk = "otherUKResident"
}

class DesConnector @Inject()(
                              httpPost: HttpClientV2Provider,
                              val auditService: AuditService,
                              val appContext: AppContext
                            )(using ec: ExecutionContext)
  extends IntegrationPlatformConnector(httpPost, auditService, appContext) {

  lazy val baseUrl: String = appContext.servicesConfig.baseUrl("des")
  lazy val integrationPlatformUri: String = s"$baseUrl/individuals/residency-status/"
  lazy val desAuthToken: String = appContext.desAuthToken

  def correlationId(using hc: HeaderCarrier): String = {
    val CorrelationIdPattern = """.*([A-Za-z0-9]{8}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}).*""".r
    hc.requestId match {
      case Some(requestId) =>
        requestId.value match {
          case CorrelationIdPattern(prefix) => prefix + "-" + generateNewUUID.substring(24)
          case _ => generateNewUUID
        }
      case _ => generateNewUUID
    }
  }

  val desHeaders = Seq(
    "Environment" -> desUrlHeaderEnv,
    "OriginatorId" -> "DA_RAS",
    "Content-Type" -> "application/json",
    "authorization" -> s"Bearer $desAuthToken",
    "CorrelationId" -> correlationId
  )

  def getResidencyStatus(
                          member: IndividualDetails,
                          userId: String,
                          isBulkRequest: Boolean = false
                        ): Future[Either[ResidencyStatus, ResidencyStatusFailure]] =
    getIPResidencyStatus(member, userId, desHeaders, isBulkRequest)
}

class HipConnector @Inject()(
                              httpPost: HttpClientV2Provider,
                              val auditService: AuditService,
                              val appContext: AppContext
                            )(using ec: ExecutionContext)
  extends IntegrationPlatformConnector(httpPost, auditService, appContext) {

  lazy val baseUrl: String = appContext.servicesConfig.baseUrl("hip")
  lazy val integrationPlatformUri: String = s"$baseUrl/paye/person/regional-residency-status)"

  lazy val clientId: String = appContext.servicesConfig.getString("microservice.services.hip.clientId")
  lazy val clientSecret: String = appContext.servicesConfig.getString("microservice.services.hip.clientSecret")
  lazy val authSecret: String = Base64.getEncoder.encodeToString(s"$clientId:$clientSecret".getBytes("UTF-8"))

  val hipHeaders = Seq(
//    "Content-Type" -> "application/json",
    "Authorization" -> s"Basic $authSecret",
    "CorrelationId" -> generateNewUUID,
    "X-Receipt-Date" -> DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS)),
    "X-Transmitting-System" -> "HIP"
  )

  def getResidencyStatus(
                          member: IndividualDetails,
                          userId: String,
                          isBulkRequest: Boolean = false
                        ): Future[Either[ResidencyStatus, ResidencyStatusFailure]] =
    getIPResidencyStatus(member, userId, hipHeaders, isBulkRequest)

}
