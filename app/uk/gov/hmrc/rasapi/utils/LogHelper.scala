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

package uk.gov.hmrc.rasapi.utils

import uk.gov.hmrc.rasapi.models.ConnectorFailure
import uk.gov.hmrc.http.HeaderCarrier

object LogHelper {

  private def requestId(hc: HeaderCarrier): String = hc.requestId.map(id => s"requestId=${id.value}").getOrElse("")

  def successText(tag: String, userId: String, details: String = "")(implicit hc: HeaderCarrier): String =
    s"$tag outcome=SUCCESS userId=$userId $details ${requestId(hc)}".trimWhitespace

  def failureText(tag: String, userId: String, failure: ConnectorFailure, message: String = "")(implicit
    hc: HeaderCarrier
  ): String = {

    val status      = failure.status.map(s => s"status=$s").getOrElse("")
    val parseErrors = failure.parseErrors.map(e => s"parseErrors=$e").getOrElse("")
    val cause       = failure.cause.map(c => s"cause=${c.getClass.getSimpleName}").getOrElse("")

    (s"$tag $message outcome=FAILURE reason=${failure.reason} userId=$userId ${requestId(hc)} $status " +
      s"$parseErrors $cause").trimWhitespace
  }

  extension (s: String) {
    private def trimWhitespace: String = s.replaceAll("\\s+", " ").trim
  }

}
