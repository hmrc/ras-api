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

import uk.gov.hmrc.rasapi.models.CustomerDetails


trait CachingConnector {

  def getCachedData(uuid: String): Option[CustomerDetails] = {

    uuid match {
      case "2800a7ab-fe20-42ca-98d7-c33f4133cfc2" => Some(CustomerDetails("AA123456A", "First Name", "Last Name", "1989-09-29"))
      case "633e0ee7-315b-49e6-baed-d79c3dffe467" => Some(CustomerDetails("BB123456B", "John", "Smith", "1975-05-25"))
      case "77648d82-309e-484d-a310-d0ffd2997791" => Some(CustomerDetails("CC123456C", "Jane", "Doe", "1969-06-09"))
      case "79f21755-8cd4-4785-9c10-13253f7a8bb6" => Some(CustomerDetails("EE123456E", "Joe", "Bloggs", "1982-02-17"))

      case "2900a7ab-fe20-42ca-98d8-c33f4133cfc2" => Some(CustomerDetails("AB234567B", "Peter", "Armstrong", "1969-01-01"))
      case "743e0ee7-315b-49e7-baed-d79c3dffe467" => Some(CustomerDetails("BC234567C", "Steven", "Smith", "1947-08-15"))
      case "88648d82-309e-484e-a310-d0ffd2997791" => Some(CustomerDetails("CD234567D", "Simon", "Handyside", "1984-10-31"))
      case "88648d82-309e-484d-a310-d0ffd2997792" => Some(CustomerDetails("EF234567F", "Linda", "Marshall", "1966-06-21"))

      case "3000a7ab-fe20-42ca-98d9-c33f4133cfc2" => Some(CustomerDetails("AC345678C", "Kelly", "Thompslon", "1990-02-15"))
      case "853e0ee7-315b-49e8-baed-d79c3dffe467" => Some(CustomerDetails("BD345678D", "Simon", "Handyside", "1984-10-31"))
      case "99648d82-309e-484f-a310-d0ffd2997791" => Some(CustomerDetails("CE345678E", "Zack", "Jackson", "1966-04-04"))
      case "99648d82-309e-484d-a310-d0ffd2997793" => Some(CustomerDetails("EF234567F", "Linda", "Marshall", "1966-06-21"))

      case "3100a7ab-fe20-42ca-98d1-c33f4133cfc2" => Some(CustomerDetails("AD456789D", "Oscar", "Smith", "1986-06-14"))
      case "963e0ee7-315b-49e-baed-d79c3dffe467"  => Some(CustomerDetails("BE456789E", "Louise", "Oscar", "1966-04-04"))
      case "11648d82-309e-484g-a310-d0ffd2997791" => Some(CustomerDetails("CF456789F", "Raj", "Patel", "1984-10-31"))
      case "76648d82-309e-484d-a310-d0ffd2997794" => Some(CustomerDetails("EH456789H", "Mary", "Brown", "1982-02-17"))

      case _ => None
    }
  }
}

object CachingConnector extends CachingConnector
