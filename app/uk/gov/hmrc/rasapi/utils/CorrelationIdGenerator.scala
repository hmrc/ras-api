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

import uk.gov.hmrc.http.HeaderCarrier

import java.util.UUID.randomUUID
import javax.inject.Inject

class CorrelationIdGenerator @Inject() () {

  // Number of leading request-id characters reused as the UUID's first two groups (8 + 4).
  private val TracePrefixLength = 12

  def generateNewUUID: String = randomUUID.toString

  /** Builds a UUID-shaped correlation id for a downstream call. The first 12 alphanumeric characters of the incoming
    * request id form the leading two groups (8-4), so related calls that share a request id stay traceable, while the
    * remaining three groups are randomised per call so each downstream operation still gets a unique id. Falls back to
    * a fully random UUID when the request id has fewer than 12 usable characters or is absent.
    *
    * e.g. requestId "T59jlUF4VjXG-17zf...==" -> "T59jlUF4-VjXG-<random>-<random>-<random>"
    */
  def correlationId(using hc: HeaderCarrier): String =
    hc.requestId
      .map(_.value.filter(_.isLetterOrDigit))
      .filter(_.length >= TracePrefixLength) match {
      case Some(usable) =>
        val prefix = usable.take(TracePrefixLength)
        s"${prefix.substring(0, 8)}-${prefix.substring(8, 12)}-${generateNewUUID.substring(14)}"
      case None         =>
        generateNewUUID
    }

}
