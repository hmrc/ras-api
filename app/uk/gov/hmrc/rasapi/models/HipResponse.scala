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

package uk.gov.hmrc.rasapi.models

import play.api.libs.json.{JsError, JsResult, JsString, JsSuccess, Json, Reads}

sealed abstract class HipResponse(val residencyStatus: Option[ResidencyStatus])

case class HipSuccessfulResponse(override val residencyStatus: Option[ResidencyStatus])
    extends HipResponse(residencyStatus)

case object HipInternalServerErrorResponse extends HipResponse(residencyStatus = None)

case object HipNotFoundResponse extends HipResponse(residencyStatus = None)

case class HipErrorResource(code: String, reason: String)

object HipErrorResource {
  given hipErrorResourceReads: Reads[HipErrorResource] = Json.reads[HipErrorResource]
}

case class HipErrorResponse(description: String, failures: Seq[HipErrorResource])

object HipErrorResponse {
  given hipErrorResponseReads: Reads[HipErrorResponse] = Json.reads[HipErrorResponse]
}

case class HipHoDErrorResource(`type`: String, reason: String)

object HipHoDErrorResource {
  given hipHoDErrorResourceReads: Reads[HipHoDErrorResource] = Json.reads[HipHoDErrorResource]
}

case class HipHoDFailure(failures: Seq[HipHoDErrorResource])

object HipHoDFailure {
  given hipHoDFailureReads: Reads[HipHoDFailure] = Json.reads[HipHoDFailure]
}

case class HipHoDErrorResponse(origin: String, response: HipHoDFailure)

object HipHoDErrorResponse {
  given HipHoDErrorResponseReads: Reads[HipHoDErrorResponse] = Json.reads[HipHoDErrorResponse]
}

type HipBadRequestResponse = HipErrorResponse
