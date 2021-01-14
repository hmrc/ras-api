/*
 * Copyright 2021 HM Revenue & Customs
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

import akka.actor.ActorSystem
import javax.inject.Inject
import play.api.Configuration
import play.api.libs.ws.{WSClient, WSResponse}
import uk.gov.hmrc.auth.core.PlayAuthConnector
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.audit.http.HttpAuditing
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient

import scala.concurrent.Future

class WSHttp @Inject()(httpAuditing: HttpAuditing, wsClient: WSClient, configuration: Configuration, actorSystem: ActorSystem)
  extends DefaultHttpClient(configuration, httpAuditing, wsClient, actorSystem) {
  def buildRequestWithStream(uri: String)(implicit hc: HeaderCarrier): Future[WSResponse] = buildRequest(uri).stream()
}

class RasAuthConnector @Inject()(appContext: AppContext, val http: DefaultHttpClient)
  extends PlayAuthConnector {
  lazy val serviceUrl: String = appContext.servicesConfig.baseUrl("auth")
}
