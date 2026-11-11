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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import play.api.libs.json.{JsError, JsNumber, JsResult, JsString, JsSuccess}

import java.time.LocalDate

class ModelsSpec extends AnyWordSpec with Matchers {

  private def firstErrorMessage(result: JsResult[?]): String =
    result match {
      case JsError(errors) => errors.head._2.head.message
      case other           => fail(s"expected a JsError but got $other")
    }

  "JsonReads.nino" should {

    "accept a valid nino" in {
      JsonReads.nino.reads(JsString("AB123456C")) shouldBe JsSuccess("AB123456C")
    }

    "reject an empty value as MISSING_FIELD" in {
      firstErrorMessage(JsonReads.nino.reads(JsString(""))) shouldBe "MISSING_FIELD"
    }

    "reject a badly formatted nino as INVALID_FORMAT" in {
      firstErrorMessage(JsonReads.nino.reads(JsString("NOTANINO"))) shouldBe "INVALID_FORMAT"
    }

    "reject a non-string value as INVALID_DATA_TYPE" in {
      firstErrorMessage(JsonReads.nino.reads(JsNumber(123))) shouldBe "INVALID_DATA_TYPE"
    }
  }

  "JsonReads.name" should {

    "accept a valid name" in {
      JsonReads.name.reads(JsString("O'Brien-Smith")) shouldBe JsSuccess("O'Brien-Smith")
    }

    "reject a blank value as MISSING_FIELD" in {
      firstErrorMessage(JsonReads.name.reads(JsString("   "))) shouldBe "MISSING_FIELD"
    }

    "reject a name containing disallowed characters as INVALID_FORMAT" in {
      firstErrorMessage(JsonReads.name.reads(JsString("John123"))) shouldBe "INVALID_FORMAT"
    }

    "reject a non-string value as INVALID_DATA_TYPE" in {
      firstErrorMessage(JsonReads.name.reads(JsNumber(1))) shouldBe "INVALID_DATA_TYPE"
    }
  }

  "JsonReads.isoDate" should {

    "accept a valid ISO date" in {
      JsonReads.isoDate.reads(JsString("2020-01-01")) shouldBe JsSuccess(LocalDate.of(2020, 1, 1))
    }

    "reject a future date as INVALID_DATE" in {
      firstErrorMessage(JsonReads.isoDate.reads(JsString("2999-12-31"))) shouldBe "INVALID_DATE"
    }

    "reject a non-ISO format as INVALID_FORMAT" in {
      firstErrorMessage(JsonReads.isoDate.reads(JsString("01/01/2020"))) shouldBe "INVALID_FORMAT"
    }

    "reject an empty value as MISSING_FIELD" in {
      firstErrorMessage(JsonReads.isoDate.reads(JsString(" "))) shouldBe "MISSING_FIELD"
    }

    "reject a non-string value as INVALID_DATA_TYPE" in {
      firstErrorMessage(JsonReads.isoDate.reads(JsNumber(2020))) shouldBe "INVALID_DATA_TYPE"
    }
  }

  "JsonReads.bulkDate" should {

    "accept all supported date formats" in {
      val expected = LocalDate.of(2020, 1, 2)
      JsonReads.bulkDate.reads(JsString("2020-01-02")) shouldBe JsSuccess(expected)
      JsonReads.bulkDate.reads(JsString("02/01/2020")) shouldBe JsSuccess(expected)
      JsonReads.bulkDate.reads(JsString("02-01-2020")) shouldBe JsSuccess(expected)
      JsonReads.bulkDate.reads(JsString("2020/01/02")) shouldBe JsSuccess(expected)
    }

    "reject an impossible date as INVALID_DATE" in {
      firstErrorMessage(JsonReads.bulkDate.reads(JsString("2020-13-01"))) shouldBe "INVALID_DATE"
    }
  }

}
