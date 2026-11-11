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
import play.api.libs.json.{JsObject, JsValue, Json, Reads, __}
import play.api.libs.ws.JsonBodyWritables.writeableOf_JsValue
import uk.gov.hmrc.http.{HeaderCarrier, *}
import uk.gov.hmrc.play.bootstrap.http.HttpClientV2Provider
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.connectors.IntegrationPlatformConnector.*
import _root_.uk.gov.hmrc.rasapi.models.*
import _root_.uk.gov.hmrc.rasapi.services.AuditService
import play.api.http.Status.*

import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.UUID.randomUUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

trait IntegrationPlatformConnector @Inject() (
  httpPost: HttpClientV2Provider,
  auditService: AuditService,
  appContext: AppContext
)(using ec: ExecutionContext)
    extends Logging {

  def baseUrl: String
  def integrationPlatformUri: String
  def createPayload(individualDetails: IndividualDetails, userId: String): JsValue
  def generateNewUUID: String = randomUUID.toString

  def getResidencyStatus(
    member: IndividualDetails,
    userId: String,
    isBulkRequest: Boolean = false
  ): Future[Either[ResidencyStatus, ResidencyStatusFailure]]

  def resolveFailureResponse(response: HttpResponse, t: Throwable): Either[ResidencyStatus, ResidencyStatusFailure]

  given rasHeaders: HeaderCarrier = HeaderCarrier()

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
  lazy val isHip5161Enabled: Boolean         = appContext.isHip5161Enabled
  lazy val integrationPlatform: String       = if (isHip5161Enabled) "HIP" else "DES"

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
          s"[ResultsGenerator][getResultAndProcess] Did not receive a result from $integrationPlatform, retry count: $retryCount for userId ($userId)."
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
    integrationPlatformHeaders: Seq[(String, String)]
  )(using rasHeaders: HeaderCarrier): Future[Either[ResidencyStatus, ResidencyStatusFailure]] = {

    val result = httpPost
      .get()
      .post(url"$uri")
      .setHeader(integrationPlatformHeaders*)
      .withBody(createPayload(member, userId))
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
            s"[IntegrationPlatformConnector][getResidencyStatus] Bad Request returned from $integrationPlatform. The details sent were not " +
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
            s"[IntegrationPlatformConnector][getResidencyStatus] Request could not be sent 429 (Too Many Requests) was sent " +
              s"from the HoD. userId ($userId)."
          )
          Right(ResidencyStatusFailure(error_TooManyRequests, "Too Many Requests."))

        case _: GatewayTimeoutException =>
          logger.error(s"[IntegrationPlatformConnector][getResidencyStatus] Request has timed out. userId ($userId).")
          Right(ResidencyStatusFailure(error_DoNotReProcess, "Internal server error."))

        case errorResponse: UpstreamErrorResponse =>
          if (errorResponse.statusCode == 503) {
            logger.error(s"[IntegrationPlatformConnector][getResidencyStatus] Service unavailable. userId ($userId).")
            Right(ResidencyStatusFailure(error_ServiceUnavailable, "Service unavailable"))
          } else {
            logger.error(
              s"[IntegrationPlatformConnector][getResidencyStatus] ${errorResponse.statusCode} was returned from $integrationPlatform. userId ($userId)."
            )
            Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
          }

        case th: Throwable =>
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
      case Failure(ex)      => resolveFailureResponse(httpResponse, ex)
    }

}

object IntegrationPlatformConnector {
  val uk       = "Uk"
  val scot     = "Scottish"
  val scotRes  = "scotResident"
  val welsh    = "Welsh"
  val welshRes = "welshResident"
  val otherUk  = "otherUKResident"
}

class DesConnector @Inject() (
  httpPost: HttpClientV2Provider,
  val auditService: AuditService,
  val appContext: AppContext
)(using ec: ExecutionContext)
    extends IntegrationPlatformConnector(httpPost, auditService, appContext) {

  lazy val baseUrl: String                = appContext.servicesConfig.baseUrl("des")
  lazy val integrationPlatformUri: String = s"$baseUrl/individuals/residency-status/"

  lazy val desAuthToken: String    = appContext.desAuthToken
  lazy val desUrlHeaderEnv: String = appContext.desUrlHeaderEnv

  def createPayload(individualDetails: IndividualDetails, userId: String): JsValue =
    Json.toJson(
      Json
        .toJson[IndividualDetails](individualDetails)
        .as[JsObject] + ("pensionSchemeOrganisationID" -> Json.toJson(userId))
    )

  def resolveFailureResponse(
    httpResponse: HttpResponse,
    t: Throwable
  ): Either[ResidencyStatus, ResidencyStatusFailure] =
    /*
       non-2XX responses produce a future failure so would not ordinarily map here.
       Rather tests that mock a future success for non-2XX responses will map here instead.
     */
    Try(httpResponse.json.as[ResidencyStatusFailure](ResidencyStatusFormats.failureFormats)) match {
      case Success(data) =>
        logger.debug(
          s"[IntegrationPlatformConnector][resolveJsonResponse] IntegrationPlatformFailureResponse from $integrationPlatform :$data"
        )
        Right(data)
      case Failure(ex)   =>
        logger.error(
          s"[IntegrationPlatformConnector][resolveResponse] Error from $integrationPlatform :${ex.getMessage}",
          ex
        )
        Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
    }

  def correlationId(using hc: HeaderCarrier): String = {
    val CorrelationIdPattern = """.*([A-Za-z0-9]{8}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}).*""".r
    hc.requestId match {
      case Some(requestId) =>
        requestId.value match {
          case CorrelationIdPattern(prefix) => prefix + "-" + generateNewUUID.substring(24)
          case _                            => generateNewUUID
        }
      case _               => generateNewUUID
    }
  }

  private lazy val desHeaders = Seq(
    "Environment"   -> desUrlHeaderEnv,
    "OriginatorId"  -> "DA_RAS",
    "Content-Type"  -> "application/json",
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

class HipConnector @Inject() (
  httpPost: HttpClientV2Provider,
  val auditService: AuditService,
  val appContext: AppContext
)(using ec: ExecutionContext)
    extends IntegrationPlatformConnector(httpPost, auditService, appContext) {

  lazy val baseUrl: String                = appContext.servicesConfig.baseUrl("hip")
  lazy val integrationPlatformUri: String = s"$baseUrl/paye/person/regional-residency-status"

  private lazy val clientID: String      = appContext.servicesConfig.getString("microservice.services.hip.clientID")
  private lazy val clientSecret: String  = appContext.servicesConfig.getString("microservice.services.hip.clientSecret")
  private lazy val authSecret: String    = Base64.getEncoder.encodeToString(s"$clientID:$clientSecret".getBytes("UTF-8"))
  private lazy val hipOriginator: String = appContext.servicesConfig.getString("microservice.services.hip.originatorId")

  private lazy val hipHeaders = Seq(
    "Authorization"         -> s"Basic $authSecret",
    "Content-Type"          -> "application/json",
    "correlationId"         -> generateNewUUID,
    "gov-uk-originator-id"  -> hipOriginator,
    "X-Receipt-Date"        -> DateTimeFormatter.ISO_INSTANT.format(Instant.now().truncatedTo(ChronoUnit.SECONDS)),
    "X-Transmitting-System" -> "HIP"
  )

  def createPayload(individualDetails: IndividualDetails, userId: String): JsValue = {
    import play.api.libs.json._

    val jsResult = Json
      .toJson[IndividualDetails](individualDetails)
      .transform {
        val renameMap = Map(
          "nino"      -> "identifier",
          "firstName" -> "firstForename",
          "lastName"  -> "surname",
          "dob"       -> "dateOfBirth"
        )
        Reads {
          case JsObject(fields) =>
            val updatedFields = fields.map { case (key, value) =>
              renameMap.getOrElse(key, key) -> value
            }
            JsSuccess(JsObject(updatedFields))
          case other            =>
            JsError(s"Expected JsObject, got $other")
        }
      }
    jsResult.get
  }

  def getResidencyStatus(
    member: IndividualDetails,
    userId: String,
    isBulkRequest: Boolean = false
  ): Future[Either[ResidencyStatus, ResidencyStatusFailure]] =
    getIPResidencyStatus(member, userId, hipHeaders, isBulkRequest)

  def resolveFailureResponse(
    httpResponse: HttpResponse,
    t: Throwable
  ): Either[ResidencyStatus, ResidencyStatusFailure] =
    /*
       As non-2XX responses produce a future failure.
       Any test that mocks a future success will throw a non-2XX exception here.
     */
    httpResponse.status match {
      case BAD_REQUEST         => throw BadRequestException(t.getMessage)
      case FORBIDDEN           => throw UpstreamErrorResponse(t.getMessage, 403)
      case NOT_FOUND           => throw NotFoundException(t.getMessage)
      case TOO_MANY_REQUESTS   => throw UpstreamErrorResponse(t.getMessage, 429)
      case REQUEST_TIMEOUT     => throw RequestTimeoutException(t.getMessage)
      case SERVICE_UNAVAILABLE => throw UpstreamErrorResponse(t.getMessage, 503)
      case _                   => throw InternalServerException(t.getMessage)
    }

}
