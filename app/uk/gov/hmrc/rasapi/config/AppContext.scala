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

package uk.gov.hmrc.rasapi.config

import play.api.{Configuration, Play}
import play.api.Mode.Mode
import play.api.Play._
import uk.gov.hmrc.play.config.ServicesConfig

object AppContext extends ServicesConfig {
  lazy val appName = current.configuration.getString("appName").getOrElse(throw new RuntimeException("appName is not configured"))
  lazy val appUrl = current.configuration.getString("appUrl").getOrElse(throw new RuntimeException("appUrl is not configured"))
  lazy val apiContext = current.configuration.getString(s"api.context").getOrElse(throw new RuntimeException(s"Missing Key api.context"))
  lazy val baseUrl = current.configuration.getString("baseUrl").getOrElse(throw new RuntimeException("Missing Key baseUrl"))
  lazy val apiStatus = current.configuration.getString("api.status").getOrElse(throw new RuntimeException(s"Missing Key api.status"))
  lazy val endpointsEnabled = current.configuration.getBoolean("api.endpointsEnabled").getOrElse(throw new RuntimeException(s"Missing key api.endpointsEnabled"))
  lazy val desAuthToken = current.configuration.getString("desauthtoken").getOrElse(throw new RuntimeException(s"Missing Key desauthtoken"))
  lazy val desUrlHeaderEnv: String =  current.configuration.getString("environment").getOrElse(throw new RuntimeException(s"Missing Key environment"))
  lazy val edhUrl: String = current.configuration.getString("endpoints.edh.url").getOrElse(throw new RuntimeException(s"Missing Key edhUrl"))
  lazy val resultsExpriyTime: Long = current.configuration.getLong("results.expiry.time").getOrElse(259200)
  lazy val allowNoNextYearStatus: Boolean = current.configuration.getBoolean("toggle-feature.allow-no-next-year-status").getOrElse(false)
  lazy val allowDefaultRUK: Boolean = current.configuration.getBoolean("toggle-feature.allow-default-ruk").getOrElse(false)
  lazy val retryEnabled: Boolean = current.configuration.getBoolean("toggle-feature.retry-enabled").getOrElse(false)
  lazy val bulkRetryEnabled: Boolean = current.configuration.getBoolean("toggle-feature.bulk-retry-enabled").getOrElse(false)
  lazy val requestRetryLimit: Int = current.configuration.getInt("request-retry-limit").getOrElse(3)
  lazy val retryDelay: Int = current.configuration.getInt("retry-delay").getOrElse(500)
  lazy val deceasedStatus: String = current.configuration.getString("status.deceased").getOrElse("DECEASED")
  lazy val tooManyRequestsStatus: String = current.configuration.getString("status.too-many-requests").getOrElse("TOO_MANY_REQUESTS")
  lazy val matchingFailedStatus: String = current.configuration.getString("status.matching-failed.api").getOrElse("STATUS_UNAVAILABLE")
  lazy val serviceUnavailableStatus: String = current.configuration.getString("status.service-unavailable").getOrElse("SERVICE_UNAVAILABLE")
  lazy val doNotReProcessStatus: String = current.configuration.getString("status.do-not-re-process").getOrElse("DO_NOT_RE_PROCESS")
  lazy val fileProcessingMatchingFailedStatus: String = current.configuration.getString("status.matching-failed.csv").getOrElse("cannot_provide_status")
  lazy val fileProcessingInternalServerErrorStatus: String = current.configuration.getString("status.internal-server-error.csv").getOrElse("problem-getting-status")
  lazy val internalServerErrorStatus: String = current.configuration.getString("status.internal-server-error.api").getOrElse("INTERNAL_SERVER_ERROR")
  lazy val removeChunksDataExerciseEnabled: Boolean = current.configuration.getBoolean("remove-chunks-data-exercise.enabled").getOrElse(false)
  lazy val apiV2_0Enabled: Boolean = current.configuration.getBoolean("api-v2_0.enabled").getOrElse(false)

  override protected def mode: Mode = Play.current.mode

  override protected def runModeConfiguration: Configuration = Play.current.configuration
}
