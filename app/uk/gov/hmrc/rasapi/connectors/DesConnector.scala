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

package uk.gov.hmrc.rasapi.connectors

import javax.inject.Inject
import play.api.{Configuration, Logger, Play}
import play.api.libs.json.{JsObject, JsValue, Json, Writes}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext
import uk.gov.hmrc.rasapi.config.{AppContext, WSHttp}
import uk.gov.hmrc.rasapi.models._
import uk.gov.hmrc.rasapi.services.AuditService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class DesConnector @Inject()(
                              httpPost: DefaultHttpClient,
                              val auditService: AuditService,
                              val appContext: AppContext,
                              implicit val ec: ExecutionContext
                            ) {

  val uk = "Uk"
  val scot = "Scottish"
  val scotRes = "scotResident"
  val welsh = "Welsh"
  val welshRes = "welshResident"
  val otherUk = "otherUKResident"


  lazy val desBaseUrl: String = appContext.servicesConfig.baseUrl("des")
  lazy val edhUrl: String = desBaseUrl + appContext.edhUrl
  lazy val error_InternalServerError: String = appContext.internalServerErrorStatus
  lazy val error_Deceased: String = appContext.deceasedStatus
  lazy val error_MatchingFailed: String = appContext.matchingFailedStatus
  lazy val error_DoNotReProcess: String = appContext.doNotReProcessStatus
  lazy val error_ServiceUnavailable: String = appContext.serviceUnavailableStatus
  lazy val allowNoNextYearStatus: Boolean = appContext.allowNoNextYearStatus
  lazy val retryLimit: Int = appContext.requestRetryLimit
  lazy val desUrlHeaderEnv: String = appContext.desUrlHeaderEnv
  lazy val desAuthToken: String = appContext.desAuthToken
  lazy val retryDelay: Int = appContext.retryDelay
  lazy val isRetryEnabled: Boolean = appContext.retryEnabled
  lazy val isBulkRetryEnabled: Boolean = appContext.bulkRetryEnabled
  lazy val error_TooManyRequests: String = appContext.tooManyRequestsStatus

  lazy val nonRetryableErrors = List(error_MatchingFailed, error_Deceased, error_DoNotReProcess)

  def canRetryRequest(isBulkRequest: Boolean): Boolean = isRetryEnabled || (isBulkRetryEnabled && isBulkRequest)

  def isCodeRetryable(code: String, isBulkRequest: Boolean): Boolean = {
    canRetryRequest(isBulkRequest) && !nonRetryableErrors.contains(code)
  }

  def getResidencyStatus(member: IndividualDetails, userId: String, apiVersion: ApiVersion, isBulkRequest: Boolean = false):
  Future[Either[ResidencyStatus, ResidencyStatusFailure]] = {

    implicit val rasHeaders: HeaderCarrier = HeaderCarrier()

    val uri = s"${desBaseUrl}/individuals/residency-status/"

    val desHeaders = Seq("Environment" -> desUrlHeaderEnv,
      "OriginatorId" -> "DA_RAS",
      "Content-Type" -> "application/json",
      "authorization" -> s"Bearer ${desAuthToken}")

    def getResultAndProcess(memberDetails: IndividualDetails, retryCount: Int = 1): Future[Either[ResidencyStatus, ResidencyStatusFailure]] = {

      if (retryCount > 1) {
        Logger.warn(s"[ResultsGenerator][getResultAndProcess] Did not receive a result from des, retry count: $retryCount for userId ($userId).")
      }

      sendResidencyStatusRequest(uri, member, userId, desHeaders, apiVersion)(rasHeaders) flatMap {
        case Left(result) => Future.successful(Left(result))
        case Right(result) if retryCount < retryLimit && isCodeRetryable(result.code, isBulkRequest) =>
          Thread.sleep(retryDelay) //Delay before sending to HoD to try to avoid transactions per second(tps) clash
          getResultAndProcess(memberDetails, retryCount + 1)
        case Right(result) if retryCount >= retryLimit || !isCodeRetryable(result.code, isBulkRequest) =>
          Future.successful(Right(result.copy(code = result.code.replace(error_DoNotReProcess, error_InternalServerError))))
      }
    }

    getResultAndProcess(member)
  }

  private def sendResidencyStatusRequest(uri: String, member: IndividualDetails, userId: String,
                                         desHeaders: Seq[(String, String)], apiVersion: ApiVersion)(implicit rasHeaders: HeaderCarrier): Future[Either[ResidencyStatus, ResidencyStatusFailure]] = {

    val payload = Json.toJson(Json.toJson[IndividualDetails](member)
      .as[JsObject] + ("pensionSchemeOrganisationID" -> Json.toJson(userId)))

    val result = httpPost.POST[JsValue, HttpResponse](uri, payload, desHeaders)
    (implicitly[Writes[IndividualDetails]], implicitly[HttpReads[HttpResponse]], rasHeaders,
      MdcLoggingExecutionContext.fromLoggingDetails(rasHeaders))

    result.map(response =>
      resolveResponse(response, userId, member.nino, apiVersion)
    ).recover {
      case badRequestEx: BadRequestException =>
        Logger.error(s"[DesConnector][getResidencyStatus] Bad Request returned from des. The details sent were not " +
          s"valid. userId ($userId).")
        Right(ResidencyStatusFailure(error_DoNotReProcess, "Internal server error."))
      case notFoundEx: NotFoundException =>
        Right(ResidencyStatusFailure(error_MatchingFailed, "Cannot provide a residency status for this pension scheme member."))
      case Upstream4xxResponse(_, 429, _, _) =>
        Logger.error(s"[DesConnector][getResidencyStatus] Request could not be sent 429 (Too Many Requests) was sent " +
          s"from the HoD. userId ($userId).")
        Right(ResidencyStatusFailure(error_TooManyRequests, "Too Many Requests."))
      case requestTimeOutEx: RequestTimeoutException =>
        Logger.error(s"[DesConnector][getResidencyStatus] Request has timed out. userId ($userId).")
        Right(ResidencyStatusFailure(error_DoNotReProcess, "Internal server error."))
      case _5xx: Upstream5xxResponse =>
        if (_5xx.upstreamResponseCode == 503) {
          Logger.error(s"[DesConnector][getResidencyStatus] Service unavailable. userId ($userId).")
          Right(ResidencyStatusFailure(error_ServiceUnavailable, "Service unavailable"))
        } else {
          Logger.error(s"[DesConnector][getResidencyStatus] ${_5xx.upstreamResponseCode} was returned from DES. userId ($userId).")
          Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
        }
      case th: Throwable =>
        Logger.error(s"[DesConnector][getResidencyStatus] Caught error occurred when calling the HoD. userId ($userId).Exception message: ${th.getMessage}.")
        Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
    }
  }

  private def convertResidencyStatus(residencyStatus: String, apiVersion: ApiVersion): String = {
    val ukAndScotRes = residencyStatus.replace(uk, otherUk).replace(scot, scotRes)
    apiVersion match {
      case V1_0 =>
        ukAndScotRes.replace(welsh, otherUk)
      case V2_0 =>
        ukAndScotRes.replace(welsh, welshRes)
    }
  }

  private def resolveResponse(httpResponse: HttpResponse, userId: String, nino: NINO, apiVersion: ApiVersion)
                             (implicit hc: HeaderCarrier): Either[ResidencyStatus, ResidencyStatusFailure] = {

    Try(httpResponse.json.as[ResidencyStatusSuccess](ResidencyStatusFormats.successFormats)) match {
      case Success(payload) =>
        payload.deseasedIndicator match {
          case Some(true) => Right(ResidencyStatusFailure(error_Deceased, "Cannot provide a residency status for this pension scheme member."))
          case _ => {
            if (payload.nextYearResidencyStatus.isEmpty && !allowNoNextYearStatus) {
              val auditDataMap = Map("userId" -> userId,
                "nino" -> nino,
                "nextYearResidencyStatus" -> "NOT_PRESENT")

              auditService.audit(auditType = "ReliefAtSourceAudit_DES_Response",
                path = "PATH_NOT_DEFINED",
                auditData = auditDataMap
              )

              Right(ResidencyStatusFailure(error_DoNotReProcess, "Internal server error."))
            } else {
              val currentStatus = convertResidencyStatus(payload.currentYearResidencyStatus, apiVersion)
              val nextYearStatus: Option[String] = payload.nextYearResidencyStatus.map(convertResidencyStatus(_, apiVersion))
              Left(ResidencyStatus(currentStatus, nextYearStatus))
            }
          }
        }
      case Failure(_) =>
        Try(httpResponse.json.as[ResidencyStatusFailure](ResidencyStatusFormats.failureFormats)) match {
          case Success(data) =>
            Logger.debug(s"[DesConnector][resolveResponse] DesFailureResponse from DES :${data}")
            Right(data)
          case Failure(ex) =>
            Logger.error(s"[DesConnector][resolveResponse] Error from DES :${ex.getMessage}", ex)
            Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
        }
    }
  }
}
