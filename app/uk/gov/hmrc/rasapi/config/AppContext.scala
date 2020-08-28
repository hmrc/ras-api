/*
 * Copyright 2020 HM Revenue & Customs
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

import javax.inject.Inject
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig

class AppContext @Inject()(val servicesConfig: ServicesConfig) {
  lazy val appName = servicesConfig.getString("appName")
  lazy val appUrl = servicesConfig.getString("appUrl")
  lazy val apiContext = servicesConfig.getString(s"api.context")
  lazy val baseUrl = servicesConfig.getString("baseUrl")
  lazy val apiStatus = servicesConfig.getString("api.status")
  lazy val endpointsEnabled = servicesConfig.getBoolean("api.endpointsEnabled")
  lazy val desAuthToken = servicesConfig.getString("desauthtoken")
  lazy val desUrlHeaderEnv: String =  servicesConfig.getString("environment")
  lazy val edhUrl: String = servicesConfig.getString("endpoints.edh.url")
  lazy val resultsExpriyTime: Int = servicesConfig.getInt("results.expiry.time")
  lazy val allowNoNextYearStatus: Boolean = servicesConfig.getBoolean("toggle-feature.allow-no-next-year-status")
  lazy val allowDefaultRUK: Boolean = servicesConfig.getBoolean("toggle-feature.allow-default-ruk")
  lazy val retryEnabled: Boolean = servicesConfig.getBoolean("toggle-feature.retry-enabled")
  lazy val bulkRetryEnabled: Boolean = servicesConfig.getBoolean("toggle-feature.bulk-retry-enabled")
  lazy val requestRetryLimit: Int = servicesConfig.getInt("request-retry-limit")
  lazy val retryDelay: Int = servicesConfig.getInt("retry-delay")
  lazy val deceasedStatus: String = servicesConfig.getString("status.deceased")
  lazy val tooManyRequestsStatus: String = servicesConfig.getString("status.too-many-requests")
  lazy val matchingFailedStatus: String = servicesConfig.getString("status.matching-failed.api")
  lazy val serviceUnavailableStatus: String = servicesConfig.getString("status.service-unavailable")
  lazy val doNotReProcessStatus: String = servicesConfig.getString("status.do-not-re-process")
  lazy val fileProcessingMatchingFailedStatus: String = servicesConfig.getString("status.matching-failed.csv")
  lazy val fileProcessingInternalServerErrorStatus: String = servicesConfig.getString("status.internal-server-error.csv")
  lazy val internalServerErrorStatus: String = servicesConfig.getString("status.internal-server-error.api")
  lazy val removeChunksDataExerciseEnabled: Boolean = servicesConfig.getBoolean("remove-chunks-data-exercise.enabled")
  lazy val apiV2_0Enabled: Boolean = servicesConfig.getBoolean("api-v2_0.enabled")
}
