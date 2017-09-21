/*
 * Copyright 2017 HM Revenue & Customs
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

import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.play.http.logging.Authorization
import uk.gov.hmrc.rasapi.config.{AppContext, WSHttp}
import uk.gov.hmrc.rasapi.models._

import scala.concurrent.Future


trait DesConnector extends ServicesConfig {

  val http: HttpGet
  val desBaseUrl: String
  def getResidencyStatusUrl(nino: String): String
  val edhUrl: String

  def getResidencyStatus(nino: Nino)(implicit hc: HeaderCarrier): Future[HttpResponse] = {

    val customerNino = nino.nino
    val uri = desBaseUrl + getResidencyStatusUrl(customerNino)

    http.GET(uri)(implicitly[HttpReads[HttpResponse]], hc = updateHeaderCarrier(hc))
  }

  def sendDataToEDH(userId: String, nino: String, residencyStatus: ResidencyStatus)(implicit hc: HeaderCarrier): Future[HttpResponse] = {

    http.GET(edhUrl)(implicitly[HttpReads[HttpResponse]], hc = updateHeaderCarrier(hc))
  }

//  private val httpReads: HttpReads[HttpResponse] = new HttpReads[HttpResponse] {
//    override def read(method: String, url: String, response: HttpResponse) = response
//  }

  private def updateHeaderCarrier(headerCarrier: HeaderCarrier) =
    headerCarrier.copy(extraHeaders = Seq(("Environment" -> AppContext.desUrlHeaderEnv)),
      authorization = Some(Authorization(s"Bearer ${AppContext.desAuthToken}")))
}

object DesConnector extends DesConnector{
  // $COVERAGE-OFF$Trivial and never going to be called by a test that uses it's own object implementation
  override val http: HttpGet = WSHttp
  override val desBaseUrl = baseUrl("des")
  override def getResidencyStatusUrl(nino: String) = String.format(AppContext.residencyStatusUrl, nino) //"/ras-stubs/get-residency-status"
  override val edhUrl: String = desBaseUrl + AppContext.edhUrl
  // $COVERAGE-ON$
}
