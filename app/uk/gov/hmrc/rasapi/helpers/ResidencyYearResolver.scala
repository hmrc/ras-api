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

package uk.gov.hmrc.rasapi.helpers

import org.joda.time.DateTime

object ResidencyYearResolver extends ResidencyYearResolver

trait ResidencyYearResolver {

  def currentDateTime: DateTime = DateTime.now()

  /**
    * Checks to see if the current date is between 1st January and 5th April (inclusive)
    * @return true if the date is between 1st January and 5th April (inclusive) else return false
    */
  def isBetweenJanAndApril(): Boolean = {
    val currentDate = currentDateTime
    currentDate.isAfter(new DateTime(currentDate.getYear - 1, 12, 31, 0, 0, 0, 0)) && currentDate.isBefore(new DateTime(currentDate.getYear, 4, 6, 0, 0, 0, 0))
  }
}
