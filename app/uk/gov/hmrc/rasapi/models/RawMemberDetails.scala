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

import org.joda.time.DateTime
import play.api.libs.functional.syntax._
import play.api.libs.json.{JsPath, Json, Reads, Writes}

case class RawMemberDetails(nino: String = "",firstName: String = "", lastName: String = "",  dateOfBirth: String = "")

object RawMemberDetails {
  implicit val formats = Json.format[RawMemberDetails]
}

case class IndividualDetails(nino: NINO, firstName: Name, lastName: Name, dateOfBirth: DateTime)

object IndividualDetails {

  implicit val individualDetailsReads = individualDetailsReadsWith(JsonReads.isoDate)

  implicit val individualDetailsBulkReads = individualDetailsReadsWith(JsonReads.bulkDate)

  implicit val individualDetailssWrites: Writes[IndividualDetails] = (
    (JsPath \ "nino").write[String] and
      (JsPath \ "firstName").write[String] and
      (JsPath \ "lastName").write[String] and
      (JsPath \ "dob").write[String].contramap[DateTime](_.toString("yyyy-MM-dd"))
    )(unlift(IndividualDetails.unapply))

  private def individualDetailsReadsWith(dateTimeReads: Reads[DateTime]): Reads[IndividualDetails] =
    ((JsPath \ "nino").read[NINO](JsonReads.nino).map(_.padTo(9, " ").mkString.toUpperCase) and
        (JsPath \ "firstName").read[Name](JsonReads.name).map(_.toUpperCase) and
        (JsPath \ "lastName").read[Name](JsonReads.name).map(_.toUpperCase) and
        (JsPath \ "dateOfBirth").read[DateTime](dateTimeReads).map(new DateTime(_))
      )(IndividualDetails.apply _)
}
