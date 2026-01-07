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

import play.api.libs.json._
import play.api.libs.functional.syntax._
case class CreateFileSessionRequest(userId: String, reference: String)

object CreateFileSessionRequest {
  def nonEmptyString(fieldName: String): Reads[String] = Reads.StringReads.filter(JsonValidationError(s"$fieldName cannot be empty"))(_.nonEmpty)

  implicit val reads: Reads[CreateFileSessionRequest] = (
    (__ \ "userId").read[String](nonEmptyString("userId")) and
      (__ \ "reference").read[String](nonEmptyString("reference"))
    )(CreateFileSessionRequest.apply _)

  implicit val writes: OWrites[CreateFileSessionRequest] = Json.writes[CreateFileSessionRequest]
  implicit val format: OFormat[CreateFileSessionRequest] = OFormat(reads, writes)
}
