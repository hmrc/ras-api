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

import java.util.UUID
import java.util.UUID.randomUUID
import javax.inject.Inject

class CorrelationIdGenerator @Inject() () {

  // The first three groups of a standard 8-4-4-4-12 UUID, as it may appear anywhere within a request id.
  private val requestIdPattern = """([A-Za-z0-9]{8}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4})""".r

  def generateNewUUID: String = randomUUID.toString

  /** Derives a correlation id for a downstream call. When the incoming request id contains a UUID-shaped prefix it is
    * preserved and combined with a fresh random 12-character suffix, so the downstream call can be traced back to the
    * originating request; otherwise a brand new UUID is generated.
    */
  def correlationId(using hc: HeaderCarrier): String =
    try {
      val candidateUUID = hc.requestId
        .map(_.value)
        .flatMap(rid => requestIdPattern.findFirstMatchIn(rid).map(_.group(1)))
        .map(prefix => s"$prefix-${generateNewUUID.takeRight(12)}")
        .getOrElse(generateNewUUID)

      // Validate the candidate; a non-UUID prefix would otherwise yield an invalid correlation id.
      UUID.fromString(candidateUUID)
      candidateUUID
    } catch {
      case _: Exception => generateNewUUID
    }

}
