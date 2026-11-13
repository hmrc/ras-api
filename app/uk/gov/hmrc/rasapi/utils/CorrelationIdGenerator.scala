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

  def generateNewUUID: String = randomUUID.toString

  /** Derives a correlation id for a downstream call. When the incoming request id matches the standard 8-4-4-4 UUID
    * prefix it is preserved and combined with a fresh suffix so the downstream call can be traced back to the
    * originating request; otherwise a brand new UUID is generated.
    */
  def correlationId(using hc: HeaderCarrier): String = {
    val CorrelationIdPattern = """.*([A-Za-z0-9]{8}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}).*""".r
    hc.requestId match {
      case Some(requestId) =>
        requestId.value match {
          case CorrelationIdPattern(prefix) => prefix + "-" + generateNewUUID.substring(24)
          case _                            => generateNewUUID
        }
      case _               => generateNewUUID
    }
  }

}
