/*
 * Copyright 2018 HM Revenue & Customs
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

import play.api.Logger
import play.api.libs.json.{JsSuccess, JsValue, Json, Writes}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext
import uk.gov.hmrc.rasapi.config.{AppContext, WSHttp}
import uk.gov.hmrc.rasapi.models._
import uk.gov.hmrc.rasapi.services.AuditService

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

trait DesConnector extends ServicesConfig {

  val auditService: AuditService

  val httpPost: HttpPost = WSHttp
  val desBaseUrl: String

  val edhUrl: String

  val allowNoNextYearStatus: Boolean

  val uk = "Uk"
  val scot = "Scottish"
  val otherUk = "otherUKResident"
  val scotRes = "scotResident"

  val error_InternalServerError: String
  val error_Deceased: String
  val error_MatchingFailed: String
  val error_DoNotReProcess: String
  val desUrlHeaderEnv: String
  val desAuthToken: String

  val retryLimit: Int

  lazy val nonRetryableErrors = List(error_MatchingFailed, error_Deceased, error_DoNotReProcess)

  def isCodeRetryable(code: String): Boolean = !nonRetryableErrors.contains(code)

  def getResidencyStatus(member: IndividualDetails, userId: String):
    Future[Either[ResidencyStatus, ResidencyStatusFailure]] = {

    implicit val rasHeaders = HeaderCarrier()

    val uri = s"${desBaseUrl}/individuals/residency-status/"

    val desHeaders = Seq("Environment" -> desUrlHeaderEnv,
      "OriginatorId" -> "DA_RAS",
      "Content-Type" -> "application/json",
      "authorization" -> s"Bearer ${desAuthToken}")

    def getResultAndProcess(memberDetails: IndividualDetails, retryCount:Int = 1): Future[Either[ResidencyStatus, ResidencyStatusFailure]] = {

      if (retryCount > 1) {
        Logger.warn(s"[ResultsGenerator] Did not receive a result from des, retry count: $retryCount for userId ($userId).")
      }

      sendResidencyStatusRequest(uri, member, userId, desHeaders)(rasHeaders) flatMap {
        case Left(result) => Future.successful(Left(result))
        case Right(result) if retryCount < retryLimit && isCodeRetryable(result.code) =>
          getResultAndProcess(memberDetails, retryCount + 1)
        case Right(result) if retryCount >= retryLimit || !isCodeRetryable(result.code) =>
          Future.successful(Right(result.copy(code = result.code.replace(error_DoNotReProcess, error_InternalServerError))))
      }
    }

    getResultAndProcess(member)
  }

  private def sendResidencyStatusRequest(uri: String, member: IndividualDetails, userId: String,
                                         desHeaders: Seq[(String, String)])(implicit rasHeaders: HeaderCarrier): Future[Either[ResidencyStatus, ResidencyStatusFailure]] = {

    val result = httpPost.POST[JsValue, HttpResponse](uri, Json.toJson[IndividualDetails](member), desHeaders)
    (implicitly[Writes[IndividualDetails]], implicitly[HttpReads[HttpResponse]], rasHeaders,
      MdcLoggingExecutionContext.fromLoggingDetails(rasHeaders))

    result.map(response => resolveResponse(response, userId, member.nino)).recover {
      case badRequestEx: BadRequestException =>
        Logger.error(s"[DesConnector] [getResidencyStatus] Bad Request returned from des. The details sent were not " +
          s"valid. userId ($userId).")
        Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
      case notFoundEx: NotFoundException =>
        Right(ResidencyStatusFailure(error_MatchingFailed, "Cannot provide a residency status for this pension scheme member."))
      case tooManyEx: TooManyRequestException =>
        Logger.error(s"[DesConnector] [getResidencyStatus] Request could not be sent 429 (Too Many Requests) was sent " +
          s"from the HoD. userId ($userId).")
        Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
      case requestTimeOutEx: RequestTimeoutException =>
        Logger.error(s"[DesConnector] [getResidencyStatus] Request has timed out. userId ($userId).")
        Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
      case serviceUnavailable: ServiceUnavailableException =>
        Logger.error(s"[DesConnector] [getResidencyStatus] Service unavailable. userId ($userId).")
        Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
      case th: Throwable =>
        Logger.error(s"[DesConnector] [getResidencyStatus] Caught error occurred when calling the HoD. userId ($userId).Exception message: ${th.getMessage}.")
        Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
      case _ =>
        Logger.error(s"[DesConnector] [getResidencyStatus] Uncaught error occurred when calling the HoD. userId ($userId).")
        Right(ResidencyStatusFailure(error_InternalServerError, "Internal server error."))
    }
  }

  private def resolveResponse(httpResponse: HttpResponse, userId: String, nino: NINO)(implicit hc: HeaderCarrier): Either[ResidencyStatus, ResidencyStatusFailure] = {
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
              val currentStatus = payload.currentYearResidencyStatus.replace(uk, otherUk).replace(scot, scotRes)
              val nextYearStatus: Option[String] = payload.nextYearResidencyStatus.map(_.replace(uk, otherUk).replace(scot, scotRes))

              Left(ResidencyStatus(currentStatus, nextYearStatus))
            }
          }
        }
    }
  }
}

object DesConnector extends DesConnector {
  // $COVERAGE-OFF$Trivial and never going to be called by a test that uses it's own object implementation
  override val auditService = AuditService
  override val httpPost: HttpPost = WSHttp
  override val desBaseUrl = baseUrl("des")
  override val edhUrl: String = desBaseUrl + AppContext.edhUrl
  override val error_InternalServerError: String = AppContext.internalServerErrorStatus
  override val error_Deceased: String = AppContext.deceasedStatus
  override val error_MatchingFailed: String = AppContext.matchingFailedStatus
  override val error_DoNotReProcess: String = AppContext.doNotReProcessStatus
  override val allowNoNextYearStatus: Boolean = AppContext.allowNoNextYearStatus
  override val retryLimit: Int = AppContext.requestRetryLimit
  override val desUrlHeaderEnv: String = AppContext.desUrlHeaderEnv
  override val desAuthToken: String = AppContext.desAuthToken
  // $COVERAGE-ON$
}
