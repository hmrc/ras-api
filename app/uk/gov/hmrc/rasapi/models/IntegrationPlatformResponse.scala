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

sealed abstract class IntegrationPlatformResponse(val residencyStatus: Option[ResidencyStatus])

case class SuccessfulIntegrationPlatformResponse(override val residencyStatus: Option[ResidencyStatus])
    extends IntegrationPlatformResponse(residencyStatus)

case object InternalServerErrorResponse extends IntegrationPlatformResponse(residencyStatus = None)

case object NotFoundResponse extends IntegrationPlatformResponse(residencyStatus = None)

case class HipErrorResource(code: String, reason: String)
object HipErrorResource {
  given hipErrorResourceReads: Reads[HipErrorResource] = Json.reads[HipErrorResource]
}

case class Hip422ErrorResponse(description: String, failures: Seq[HipErrorResource])
object Hip422ErrorResponse {
  given hip422ErrorResponseReads: Reads[Hip422ErrorResponse] = Json.reads[Hip422ErrorResponse]
}

case class HipHIPErrorResource(`type`: String, reason: String)
object HipHIPErrorResource {
  given hipHIPErrorResourceReads: Reads[HipHIPErrorResource] = Json.reads[HipHIPErrorResource]
}

case class HipHoDFailure(failures: Seq[HipErrorResource])
object HipHoDFailure {
  given hipHoDFailureReads: Reads[HipHoDFailure] = Json.reads[HipHoDFailure]
}

case class HipHoDErrorResponse(origin: String, response: HipHoDFailure)
object HipHoDErrorResponse {
  given hipHoDErrorResponseReads: Reads[HipHoDErrorResponse] = Json.reads[HipHoDErrorResponse]
}

case class HipHIPFailure(failures: Seq[HipHIPErrorResource])
object HipHIPFailure {
  given hipHIPFailureReads: Reads[HipHIPFailure] = Json.reads[HipHIPFailure]
}
case class HipHIPErrorResponse(origin: String, response: HipHIPFailure)

enum Hip422ErrorCode(val value: String) {
  case NotFound extends Hip422ErrorCode("63303")
}

//object Hip422ErrorCode {
//  implicit val hip422ErrorCodeReads: Reads[Hip422ErrorCode] = Reads[Hip422ErrorCode] {
//    case JsString(code) => JsSuccess(NotFound)
//    case _              => JsError("HipValidationError code should be a string")
//  }
//}

