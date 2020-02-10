/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.rasapi.models

import play.api.libs.json.Json

case class ResidencyStatusResponse(success: Option[ResidencyStatusSuccess], failure: Option[ResidencyStatusFailure])

case class ResidencyStatusSuccess(nino: String, deathDate: Option[String], deathDateStatus: Option[String],
                                  deseasedIndicator: Option[Boolean], currentYearResidencyStatus: String, nextYearResidencyStatus: Option[String])

case class ResidencyStatusFailure(code: String, reason: String)

object ResidencyStatusFormats {
  implicit val successFormats = Json.format[ResidencyStatusSuccess]
  implicit val failureFormats = Json.format[ResidencyStatusFailure]
}


