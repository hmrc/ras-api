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

package uk.gov.hmrc.rasapi.services

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import uk.gov.hmrc.rasapi.connectors.DesConnector
import uk.gov.hmrc.rasapi.helpers.ResidencyYearResolver
import uk.gov.hmrc.rasapi.models.RawMemberDetails

import java.time.LocalDate

class ResultsGeneratorSpec extends AnyWordSpecLike with Matchers with MockitoSugar {

  class FailingParseResultsGenerator extends ResultsGenerator {
    val desConnector: DesConnector                    = mock[DesConnector]
    val residencyYearResolver: ResidencyYearResolver  = mock[ResidencyYearResolver]
    val auditService: AuditService                    = mock[AuditService]
    def getCurrentDate: LocalDate                     = LocalDate.now()
    val allowDefaultRUK: Boolean                      = false
    val DECEASED: String                              = ""
    val MATCHING_FAILED: String                       = ""
    val INTERNAL_SERVER_ERROR: String                 = ""
    val SERVICE_UNAVAILABLE: String                   = ""
    val FILE_PROCESSING_MATCHING_FAILED: String       = ""
    val FILE_PROCESSING_INTERNAL_SERVER_ERROR: String = ""

    override def parseString(inputRow: String): RawMemberDetails = null
  }

  "ResultsGenerator.createMatchingData" should {
    "fall back to 'INVALID RECORD' when JSON serialization throws" in {
      val sut = new FailingParseResultsGenerator
      sut.createMatchingData("ignored") shouldBe Right(Seq("INVALID RECORD"))
    }
  }

}
