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

import uk.gov.hmrc.rasapi.models.{CustomerDetails, ResidencyStatus}

trait DESConnector {

  def getResidencyStatus(customerDetails: CustomerDetails): Option[ResidencyStatus] = {

    customerDetails match {
      case CustomerDetails("AA123456A", "First Name", "Last Name", "1989-09-29") => Some(ResidencyStatus("otherUKResident","otherUKResident"))
      case CustomerDetails("BB123456B", "John", "Smith", "1975-05-25") => Some(ResidencyStatus("otherUKResident","scotResident"))
      case CustomerDetails("CC123456C", "Jane", "Doe", "1969-06-09") => Some(ResidencyStatus("scotResident","otherUKResident"))
      case CustomerDetails("EE123456E", "Joe", "Bloggs", "1982-02-17") => Some(ResidencyStatus("scotResident","scotResident"))

      case CustomerDetails("AB234567B", "Peter", "Armstrong", "1969-01-01") => Some(ResidencyStatus("otherUKResident","otherUKResident"))
      case CustomerDetails("BC234567C", "Steven", "Smith", "1947-08-15") => Some(ResidencyStatus("otherUKResident","scotResident"))
      case CustomerDetails("CD234567D", "Simon", "Handyside", "1984-10-31") => Some(ResidencyStatus("scotResident","otherUKResident"))
      case CustomerDetails("EF234567F", "Linda", "Marshall", "1966-06-21") => Some(ResidencyStatus("scotResident","scotResident"))

      case CustomerDetails("AC345678C", "Kelly", "Thompson", "1990-02-15") => Some(ResidencyStatus("otherUKResident","otherUKResident"))
      case CustomerDetails("BD345678D", "Simon", "Handyside", "1984-10-31") => Some(ResidencyStatus("otherUKResident","scotResident"))
      case CustomerDetails("CE345678E", "Zack", "Jackson", "1966-04-04") => Some(ResidencyStatus("scotResident","otherUKResident"))
      case CustomerDetails("EG345678G", "Linda", "Marshall", "1966-06-21") => Some(ResidencyStatus("scotResident","scotResident"))

      case CustomerDetails("AD456789D", "Oscar", "Smith", "1986-06-14") => Some(ResidencyStatus("otherUKResident","otherUKResident"))
      case CustomerDetails("BE456789E", "Louise", "Oscar", "1966-04-04") => Some(ResidencyStatus("otherUKResident","scotResident"))
      case CustomerDetails("CF456789F", "Raj", "Patel", "1984-10-31") => Some(ResidencyStatus("scotResident","otherUKResident"))
      case CustomerDetails("EH456789H", "Mary", "Brown", "1982-02-17") => Some(ResidencyStatus("scotResident","scotResident"))

      case _ => None
    }
  }

}

object DESConnector extends DESConnector
