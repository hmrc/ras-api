/*
 * Copyright 2025 HM Revenue & Customs
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

import java.time.LocalDate

import javax.inject.Inject

class ResidencyYearResolver @Inject()() {

  def currentDate: LocalDate = LocalDate.now()

  /**
    * Checks to see if the current date is between 1st January and 5th April (inclusive)
    * @return true if the date is between 1st January and 5th April (inclusive) else return false
    */
  def isBetweenJanAndApril: Boolean = {
    val currentYear = currentDate.getYear
    !currentDate.isBefore(LocalDate.of(currentYear, 1, 1)) && !currentDate.isAfter(LocalDate.of(currentYear, 4, 5))
  }
}
