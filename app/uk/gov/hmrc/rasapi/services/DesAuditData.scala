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

import uk.gov.hmrc.rasapi.models.{ConnectorFailure, NINO}

object DesAuditData {

  private def baseMap(
    userId: String,
    nino: NINO,
    corrId: String,
    attempt: Int,
    isBulkRequest: Boolean,
    outcome: String
  ): Map[String, String] =
    Map(
      "downstreamService" -> "DES",
      "userIdentifier"    -> userId,
      "nino"              -> nino,
      "correlationId"     -> corrId,
      "attempt"           -> attempt.toString,
      "requestSource"     -> (if (isBulkRequest) "FE_BULK" else "API"),
      "outcome"           -> outcome
    )

  def success(
    userId: String,
    nino: NINO,
    corrId: String,
    attempt: Int,
    isBulkRequest: Boolean,
    httpStatus: Int
  ): Map[String, String] =
    baseMap(userId, nino, corrId, attempt, isBulkRequest, "SUCCESS") + ("httpStatus" -> httpStatus.toString)

  def failure(
    userId: String,
    nino: NINO,
    corrId: String,
    attempt: Int,
    isBulkRequest: Boolean,
    failure: ConnectorFailure,
    responseCode: String // the code returned to the caller for this attempt, e.g. DO_NOT_RE_PROCESS
  ): Map[String, String] = {
    val status      = failure.status.map(s => "httpStatus" -> s.toString)
    val parseErrors = failure.parseErrors.map(e => "parseErrors" -> e)
    val cause       = failure.cause.map(c => "cause" -> c.getClass.getSimpleName)
    val errorDetail = failure.cause.flatMap(c => Option(c.getMessage)).map("errorDetail" -> _)

    baseMap(userId, nino, corrId, attempt, isBulkRequest, "FAILURE") ++
      Map("reason" -> failure.reason.toString, "responseCode" -> responseCode) ++
      status ++ parseErrors ++ cause ++ errorDetail
  }

}
