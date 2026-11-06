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
import java.util.UUID.randomUUID
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class DesConnector @Inject() (
  httpPost: HttpClientV2Provider,
  val auditService: AuditService,
  val appContext: AppContext
)(using ec: ExecutionContext)
    extends Logging {

  val uk       = "Uk"
  val scot     = "Scottish"
  val scotRes  = "scotResident"
  val welsh    = "Welsh"
  val welshRes = "welshResident"
  val otherUk  = "otherUKResident"

  val DES_DOWNSTREAM_SERVICE: String = "DES"
  val DES_CALL_AUDIT_TYPE: String    = "ReliefAtSourceDesResidencyStatus"
  val OUTCOME_SUCCESS: String        = "SUCCESS"
  val OUTCOME_FAILURE: String        = "FAILURE"

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
  )(using hc: HeaderCarrier): Future[Either[ResidencyStatus, ResidencyStatusFailure]] = {

    val uri = s"$desBaseUrl/individuals/residency-status/"

    val corrId = correlationId

    val desHeaders = Seq(
      "Environment"   -> desUrlHeaderEnv,
      "OriginatorId"  -> "DA_RAS",
      "Content-Type"  -> "application/json",
      "authorization" -> s"Bearer $desAuthToken",
      "CorrelationId" -> corrId
    )

    def getResultAndProcess(
      memberDetails: IndividualDetails,
      retryCount: Int = 1
    ): Future[Either[ResidencyStatus, ResidencyStatusFailure]] = {

      if (retryCount > 1) {
        logger.warn(
          s"[ResultsGenerator][getResultAndProcess] Did not receive a result from des, retry count: $retryCount for userId ($userId) correlationId ($corrId)."
        )
      }

      sendResidencyStatusRequest(uri, member, userId, desHeaders, corrId) flatMap {
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

  /** Emits an enriched audit of a single downstream DES residency-status call so each request can be traced
    * end-to-end across RAS-API and DES in Splunk. Records the downstream service and endpoint, the correlation id
    * and user id, the outcome, the HTTP status, failure category, response code and error detail where
    * available, the tracing headers sent (stripping out the bearer token), and the processing path at which the event
    * occurred so that missing mandatory data, invalid responses, timeouts and other failures are all recorded.
    */
  private def auditDesResidencyStatusRequest(
    userId: String,
    nino: NINO,
    corrId: String,
    desHeaders: Seq[(String, String)],
    uri: String,
    httpStatus: Option[Int],
    outcome: String,
    failureCategory: Option[String],
    responseCode: Option[String],
    errorDetail: Option[String],
    processingPath: String
  )(using hc: HeaderCarrier): Unit = {

    val auditData: Map[String, String] = Map(
      "downstreamService" -> DES_DOWNSTREAM_SERVICE,
      "endpoint"          -> uri,
      "httpMethod"        -> "POST",
      "userIdentifier"    -> userId,
      "nino"              -> nino,
      "correlationId"     -> corrId,
      "outcome"           -> outcome,
      "auditEventType"    -> DES_CALL_AUDIT_TYPE,
      "processingPath"    -> processingPath
    ) ++
      httpStatus.map(status => "httpStatus" -> status.toString) ++
      failureCategory.map("failureCategory" -> _) ++
      responseCode.map("responseCode" -> _) ++
      errorDetail.map("errorDetail" -> _) ++
      desHeaders

    auditService.audit(
      auditType = DES_CALL_AUDIT_TYPE,
      path = uri,
      auditData = auditData
    )
  }

  private def sendResidencyStatusRequest(
    uri: String,
    member: IndividualDetails,
    userId: String,
    desHeaders: Seq[(String, String)],
    corrId: String
  )(using rasHeaders: HeaderCarrier): Future[Either[ResidencyStatus, ResidencyStatusFailure]] = {

    val payload     = Json.toJson(
      Json
        .toJson[IndividualDetails](member)
        .as[JsObject] + ("pensionSchemeOrganisationID" -> Json.toJson(userId))
    )
    val result      = httpPost
      .get()
      .post(url"$uri")
      .setHeader(desHeaders*)
      .withBody(payload)
      .execute[HttpResponse]
    val recoverPath = "DesConnector.sendResidencyStatusRequest.recover"

    result.map(response => resolveResponse(response, userId, member.nino, corrId, uri, desHeaders)).recover {
      case _: BadRequestException              =>
        logger.error(
          s"[DesConnector][getResidencyStatus] Bad Request returned from des. The details sent were not " +
            s"valid. userId ($userId) correlationId ($corrId)."
        )
        auditDesResidencyStatusRequest(
          userId,
          member.nino,
          corrId,
          desHeaders,
          uri,
          httpStatus = Some(400),
          outcome = OUTCOME_FAILURE,
          failureCategory = Some("BAD_REQUEST"),
          responseCode = Some(error_DoNotReProcess),
          errorDetail = Some("The details sent were not valid."),
          processingPath = recoverPath
        )
        Right(ResidencyStatusFailure(error_DoNotReProcess, "Internal server error."))
      case _: NotFoundException                =>
        auditDesResidencyStatusRequest(
          userId,
          member.nino,
          corrId,
          desHeaders,
          uri,
          httpStatus = Some(404),
          outcome = OUTCOME_FAILURE,
          failureCategory = Some("MATCHING_FAILED"),
          responseCode = Some(error_MatchingFailed),
          errorDetail = Some("Cannot provide a residency status for this pension scheme member."),
          processingPath = recoverPath
        )
        Right(
          ResidencyStatusFailure(
            error_MatchingFailed,
            "Cannot provide a residency status for this pension scheme member."
          )
        )
      case UpstreamErrorResponse(_, 403, _, _) =>
        logger.error(
          s"[DesConnector][getResidencyStatus] Cannot provide a residency status for this pension scheme member for userId ($userId) correlationId ($corrId)."
        )
        auditDesResidencyStatusRequest(
          userId,
          member.nino,
          corrId,
          desHeaders,
          uri,
          httpStatus = Some(403),
          outcome = OUTCOME_FAILURE,
          failureCategory = Some("FORBIDDEN"),
          responseCode = Some(error_MatchingFailed),
          errorDetail = Some("Cannot provide a residency status for this pension scheme member."),
          processingPath = recoverPath
        )
        Right(
          ResidencyStatusFailure(
            error_MatchingFailed,
            "Cannot provide a residency status for this pension scheme member."
          )
        )

      case UpstreamErrorResponse(_, 429, _, _)  =>
        logger.error(
          s"[DesConnector][getResidencyStatus] Request could not be sent 429 (Too Many Requests) was sent " +
            s"from the HoD. userId ($userId) correlationId ($corrId)."
        )
        auditDesResidencyStatusRequest(
          userId,
          member.nino,
          corrId,
          desHeaders,
          uri,
          httpStatus = Some(429),
          outcome = OUTCOME_FAILURE,
          failureCategory = Some("TOO_MANY_REQUESTS"),
          responseCode = Some(error_TooManyRequests),
          errorDetail = Some("Too Many Requests."),
          processingPath = recoverPath
        )
        Right(ResidencyStatusFailure(error_TooManyRequests, "Too Many Requests."))
      case _: RequestTimeoutException           =>
        logger.error(
          s"[DesConnector][getResidencyStatus] Request has timed out. userId ($userId) correlationId ($corrId)."
        )
        auditDesResidencyStatusRequest(
          userId,
          member.nino,
          corrId,
          desHeaders,
          uri,
          httpStatus = None,
          outcome = OUTCOME_FAILURE,
          failureCategory = Some("REQUEST_TIMEOUT"),
          responseCode = Some(error_DoNotReProcess),
          errorDetail = Some("Request has timed out."),
          processingPath = recoverPath
        )
        Right(ResidencyStatusFailure(error_DoNotReProcess, "Internal server error."))
      case errorResponse: UpstreamErrorResponse =>
        if (errorResponse.statusCode == 503) {
          logger.error(
            s"[DesConnector][getResidencyStatus] Service unavailable. userId ($userId) correlationId ($corrId)."
          )
          auditDesResidencyStatusRequest(
            userId,
            member.nino,
            corrId,
            desHeaders,
            uri,
            httpStatus = Some(503),
            outcome = OUTCOME_FAILURE,
            failureCategory = Some("SERVICE_UNAVAILABLE"),
            responseCode = Some(error_ServiceUnavailable),
            errorDetail = Some("Service unavailable"),
            processingPath = recoverPath
          )
          Right(ResidencyStatusFailure(error_ServiceUnavailable, "Service unavailable"))
        } else {
          logger.error(
            s"[DesConnector][getResidencyStatus] ${errorResponse.statusCode} was returned from DES. userId ($userId) correlationId ($corrId)."
          )
          auditDesResidencyStatusRequest(
            userId,
            member.nino,
            corrId,
            desHeaders,
            uri,
            httpStatus = Some(errorResponse.statusCode),
            outcome = OUTCOME_FAILURE,
            failureCategory = Some("UPSTREAM_ERROR"),
            responseCode = Some(error_InternalServerError),
            errorDetail = Some(s"${errorResponse.statusCode} was returned from DES."),
            processingPath = recoverPath
          )
          Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
        }
      case th: Throwable                        =>
        logger.error(
          s"[DesConnector][getResidencyStatus] Caught error occurred when calling the HoD. userId ($userId) correlationId ($corrId).Exception message: ${th.getMessage}."
        )
        auditDesResidencyStatusRequest(
          userId,
          member.nino,
          corrId,
          desHeaders,
          uri,
          httpStatus = None,
          outcome = OUTCOME_FAILURE,
          failureCategory = Some("EXCEPTION"),
          responseCode = Some(error_InternalServerError),
          errorDetail = Some(th.getMessage),
          processingPath = recoverPath
        )
        Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
    }
  }

  private def convertResidencyStatus(residencyStatus: String): String =
    residencyStatus.replace(uk, otherUk).replace(scot, scotRes).replace(welsh, welshRes)

  private def resolveResponse(
    httpResponse: HttpResponse,
    userId: String,
    nino: NINO,
    corrId: String,
    uri: String,
    desHeaders: Seq[(String, String)]
  )(using hc: HeaderCarrier): Either[ResidencyStatus, ResidencyStatusFailure] = {

    val resolvePath = "DesConnector.resolveResponse"

    Try(httpResponse.json.as[ResidencyStatusSuccess](ResidencyStatusFormats.successFormats)) match {
      case Success(payload) =>
        payload.deseasedIndicator match {
          case Some(true) =>
            auditDesResidencyStatusRequest(
              userId,
              nino,
              corrId,
              desHeaders,
              uri,
              httpStatus = Some(httpResponse.status),
              outcome = OUTCOME_FAILURE,
              failureCategory = Some("DECEASED"),
              responseCode = Some(error_Deceased),
              errorDetail = Some("Cannot provide a residency status for this pension scheme member."),
              processingPath = resolvePath
            )
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
                path = uri,
                auditData = auditDataMap
              )

              auditDesResidencyStatusRequest(
                userId,
                nino,
                corrId,
                desHeaders,
                uri,
                httpStatus = Some(httpResponse.status),
                outcome = OUTCOME_FAILURE,
                failureCategory = Some("MISSING_NEXT_YEAR_STATUS"),
                responseCode = Some(error_DoNotReProcess),
                errorDetail = Some("Next year residency status not present in DES response."),
                processingPath = resolvePath
              )

              Right(ResidencyStatusFailure(error_DoNotReProcess, "Internal server error."))
            } else {
              val currentStatus                  = convertResidencyStatus(payload.currentYearResidencyStatus)
              val nextYearStatus: Option[String] =
                payload.nextYearResidencyStatus.map(convertResidencyStatus)
              auditDesResidencyStatusRequest(
                userId,
                nino,
                corrId,
                desHeaders,
                uri,
                httpStatus = Some(httpResponse.status),
                outcome = OUTCOME_SUCCESS,
                failureCategory = None,
                responseCode = None,
                errorDetail = None,
                processingPath = resolvePath
              )
              Left(ResidencyStatus(currentStatus, nextYearStatus))
            }
        }
      case Failure(_)       =>
        Try(httpResponse.json.as[ResidencyStatusFailure](ResidencyStatusFormats.failureFormats)) match {
          case Success(data) =>
            logger.debug(s"[DesConnector][resolveResponse] DesFailureResponse from DES correlationId ($corrId) :$data")
            auditDesResidencyStatusRequest(
              userId,
              nino,
              corrId,
              desHeaders,
              uri,
              httpStatus = Some(httpResponse.status),
              outcome = OUTCOME_FAILURE,
              failureCategory = Some("DES_FAILURE_RESPONSE"),
              responseCode = Some(data.code),
              errorDetail = Some(data.reason),
              processingPath = resolvePath
            )
            Right(data)
          case Failure(ex)   =>
            logger.error(
              s"[DesConnector][resolveResponse] Error from DES correlationId ($corrId) :${ex.getMessage}",
              ex
            )
            auditDesResidencyStatusRequest(
              userId,
              nino,
              corrId,
              desHeaders,
              uri,
              httpStatus = Some(httpResponse.status),
              outcome = OUTCOME_FAILURE,
              failureCategory = Some("UNEXPECTED_RESPONSE"),
              responseCode = Some(error_InternalServerError),
              errorDetail = Some(ex.getMessage),
              processingPath = resolvePath
            )
            Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
        }
    }
  }

}
