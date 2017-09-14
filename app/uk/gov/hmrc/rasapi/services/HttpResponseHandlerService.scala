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

package uk.gov.hmrc.rasapi.services

import play.api.libs.json.JsSuccess
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.rasapi.connectors.DesConnector
import uk.gov.hmrc.rasapi.models.{Nino, ResidencyStatus, ResidencyStatusSuccess}
import uk.gov.hmrc.rasapi.models.ResidencyStatusFormats.successFormats

import scala.concurrent.Future
import scala.util.{Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global


trait HttpResponseHandlerService {

  val desConnector: DesConnector

  def handleResidencyStatusResponse(nino: Nino)(implicit hc: HeaderCarrier) : Future[Either[ResidencyStatus, String]] = {

    desConnector.getResidencyStatus(nino).map { response =>
      Try(response.json.validate[ResidencyStatusSuccess]) match {
        case Success(JsSuccess(payload, _)) =>
          Left(transformResidencyStatusValues(ResidencyStatus(currentYearResidencyStatus = payload.currentYearResidencyStatus,
            nextYearForecastResidencyStatus = payload.nextYearResidencyStatus)))
        case _ => Right("")
      }
    }
  }

  private def transformResidencyStatusValues(residencyStatus: ResidencyStatus) = {

    def transformResidencyStatusValue(residencyStatus: String): String = {
      residencyStatus match {
        case "Uk" => "otherUKResident"
        case "Scottish" => "scotResident"
      }
    }

    ResidencyStatus(transformResidencyStatusValue(residencyStatus.currentYearResidencyStatus),
                    transformResidencyStatusValue(residencyStatus.nextYearForecastResidencyStatus))
  }
}

object HttpResponseHandlerService extends HttpResponseHandlerService {

  override val desConnector = DesConnector
}
