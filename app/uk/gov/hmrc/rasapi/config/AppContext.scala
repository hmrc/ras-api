/*
 * Copyright 2018 HM Revenue & Customs
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

import play.api.Play._
import uk.gov.hmrc.play.config.ServicesConfig

object AppContext extends ServicesConfig {
  lazy val appName = current.configuration.getString("appName").getOrElse(throw new RuntimeException("appName is not configured"))
  lazy val appUrl = current.configuration.getString("appUrl").getOrElse(throw new RuntimeException("appUrl is not configured"))
  lazy val serviceLocatorUrl: String = baseUrl("service-locator")
  lazy val registrationEnabled: Boolean = current.configuration.getBoolean(s"${env}.microservice.services.service-locator.enabled").getOrElse(false)
  lazy val apiContext = current.configuration.getString(s"api.context").getOrElse(throw new RuntimeException(s"Missing Key $env.api.context"))
  lazy val baseUrl = current.configuration.getString(s"$env.baseUrl").getOrElse(throw new RuntimeException(s"Missing Key $env.baseUrl"))
  lazy val apiStatus = current.configuration.getString("api.status").getOrElse(throw new RuntimeException(s"Missing Key $env.api.status"))
  lazy val endpointsEnabled = current.configuration.getBoolean("api.endpointsEnabled").getOrElse(throw new RuntimeException(s"Missing key $env.api.endpointsEnabled"))
  lazy val residencyStatusUrl = current.configuration.getString("endpoints.residency-status.url").getOrElse(throw new RuntimeException(s"Missing Key $env.endpoints.residency-status.url"))
  lazy val desAuthToken = current.configuration.getString("desauthtoken").getOrElse(throw new RuntimeException(s"Missing Key $env.desauthtoken"))
  lazy val desUrlHeaderEnv: String =  current.configuration.getString("environment").getOrElse(throw new RuntimeException(s"Missing Key $env.environment"))
  lazy val edhUrl: String = current.configuration.getString("endpoints.edh.url").getOrElse(throw new RuntimeException(s"Missing Key $env.edhUrl"))
  lazy val resultsExpriyTime: Long = current.configuration.getLong("results.expiry.time").getOrElse(259200)
  lazy val allowNoNextYearStatus: Boolean = current.configuration.getBoolean("toggle-feature.allow-no-next-year-status").getOrElse(false)
}
