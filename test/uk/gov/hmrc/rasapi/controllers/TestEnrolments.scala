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

package uk.gov.hmrc.rasapi.controllers

import uk.gov.hmrc.auth.core.{Enrolment, EnrolmentIdentifier, Enrolments}

trait TestEnrolments {
  private val enrolmentIdentifier1 = EnrolmentIdentifier("PSAID", "A123456")
  private val enrolment1 = new Enrolment(key = "HMRC-PSA-ORG", identifiers = List(enrolmentIdentifier1), state = "Activated", None)
  private val enrolmentIdentifier2 = EnrolmentIdentifier("PPID", "A123456")
  private val enrolment2 = new Enrolment(key = "HMRC-PP-ORG", identifiers = List(enrolmentIdentifier2), state = "Activated", None)
  private val enrolmentIdentifier3 = EnrolmentIdentifier("PSAID", "A123456")
  private val enrolment3 = new Enrolment(key = "HMRC-PODS-ORG", identifiers = List(enrolmentIdentifier3), state = "Activated", None)
  private val enrolmentIdentifier4 = EnrolmentIdentifier("PSPID", "A123456")
  private val enrolment4 = new Enrolment(key = "HMRC-PODSPP-ORG", identifiers = List(enrolmentIdentifier4), state = "Activated", None)
  lazy val enrolments: Enrolments = Enrolments(Set(enrolment1, enrolment2, enrolment3, enrolment4))
}
