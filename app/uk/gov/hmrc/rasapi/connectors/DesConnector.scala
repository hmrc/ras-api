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
import play.api.libs.json.{JsValue, Json, Writes}
import uk.gov.hmrc.http._
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext
import uk.gov.hmrc.rasapi.config.{AppContext, WSHttp}
import uk.gov.hmrc.rasapi.models._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}


trait DesConnector extends ServicesConfig {

  val httpGet: HttpGet
  val httpPost: HttpPost
  val desBaseUrl: String

  def getResidencyStatusUrl(nino: String): String

  val edhUrl: String

   val uk = "Uk"
   val scot = "Scottish"
   val otherUk = "otherUKResident"
   val scotRes = "scotResident"

  def getResidencyStatus(nino: Nino)(implicit hc: HeaderCarrier): Future[HttpResponse] = {

    val customerNino = nino.nino
    val uri = desBaseUrl + getResidencyStatusUrl(customerNino)

    httpGet.GET(uri)(implicitly[HttpReads[HttpResponse]], hc = updateHeaderCarrier(hc), ec = MdcLoggingExecutionContext.fromLoggingDetails)
  }

  def sendDataToEDH(userId: String, nino: String, residencyStatus: ResidencyStatus)(implicit hc: HeaderCarrier): Future[HttpResponse] = {

    httpPost.POST[EDHAudit, HttpResponse](url = edhUrl, body = EDHAudit(userId, nino,
      residencyStatus.currentYearResidencyStatus,residencyStatus.nextYearForecastResidencyStatus))(implicitly[Writes[EDHAudit]],
      implicitly[HttpReads[HttpResponse]], updateHeaderCarrier(hc), ec = MdcLoggingExecutionContext.fromLoggingDetails)
  }

  def getResidencyStatus(member: IndividualDetails)(implicit hc: HeaderCarrier):
    Future[Either[ResidencyStatus, ResidencyStatusFailure]]  = {

    val uri = s"${desBaseUrl}/pension-scheme/customers/look-up"

    val result = httpPost.POST[JsValue, HttpResponse](uri, Json.toJson[IndividualDetails](member), Seq())
    (implicitly[Writes[IndividualDetails]], implicitly[HttpReads[HttpResponse]], updateHeaderCarrier(hc),
      MdcLoggingExecutionContext.fromLoggingDetails(hc))

    result.map(response => resolveResponse(response))
  }
  private def resolveResponse(httpResponse: HttpResponse) :Either[ResidencyStatus, ResidencyStatusFailure] =   {
    Try(httpResponse.json.as[ResidencyStatusSuccess](ResidencyStatusFormats.successFormats)) match {
      case Success(payload) =>
        val currentStatus = payload.currentYearResidencyStatus.replace(uk,otherUk).replace(scot,scotRes)
        val nextYearSatus = payload.nextYearResidencyStatus.replace(uk,otherUk).replace(scot,scotRes)
        Left(ResidencyStatus(currentStatus,nextYearSatus))
      case Failure(_) =>
        Try(httpResponse.json.as[ResidencyStatusFailure](ResidencyStatusFormats.failureFormats)) match {
          case Success(data) => Logger.debug(s"DesFailureResponse from DES :${data}")
            Right(data)
          case Failure(ex) =>
            if(httpResponse.status == 404)
              Right(ResidencyStatusFailure("MATCHING_FAILED","The customer details provided did not match with HMRC’s records."))
            else {
              Logger.error(s"Error from DES :${ex.getMessage}")
              Right(ResidencyStatusFailure(ErrorInternalServerError.errorCode, ErrorInternalServerError.message))
            }
        }
    }
  }
  private def updateHeaderCarrier(headerCarrier: HeaderCarrier) =
    headerCarrier.copy(extraHeaders = Seq("Environment" -> AppContext.desUrlHeaderEnv),
      authorization = Some(Authorization(s"Bearer ${AppContext.desAuthToken}")))
}

object DesConnector extends DesConnector {
  // $COVERAGE-OFF$Trivial and never going to be called by a test that uses it's own object implementation
  override val httpGet: HttpGet = WSHttp
  override val httpPost: HttpPost = WSHttp
  override val desBaseUrl = baseUrl("des")
  override def getResidencyStatusUrl(nino: String) = String.format(AppContext.residencyStatusUrl, nino)
  override val edhUrl: String = desBaseUrl + AppContext.edhUrl
  // $COVERAGE-ON$
}
