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
import play.api.libs.json.Json
import play.api.mvc.{RequestHeader, Result}
import play.api.{Configuration, Environment, Logger, Mode}
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.http.cache.client.{SessionCache, ShortLivedCache, ShortLivedHttpCaching}
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.http.{DefaultHttpClient, JsonErrorHandler}
import play.api.mvc.Results.{BadRequest, InternalServerError, NotFound, Unauthorized}
import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, UNAUTHORIZED}
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent
import uk.gov.hmrc.rasapi.controllers.{BadRequestResponse, ErrorInternalServerError, ErrorNotFound, Unauthorised}

import scala.concurrent.{ExecutionContext, Future}


class RasShortLivedHttpCaching @Inject()(
                                          val environment: Environment,
                                          val appNameConfiguration: Configuration,
                                          val appContext: AppContext,
                                          val http: DefaultHttpClient
                                        ) extends ShortLivedHttpCaching {
  val mode: Mode.Mode = environment.mode
  override lazy val defaultSource = appContext.appName
  override lazy val baseUri = appContext.servicesConfig.baseUrl("cachable.short-lived-cache")
  override lazy val domain = appContext.servicesConfig.getConfString("cachable.short-lived-cache.domain",
    throw new Exception(s"Could not find config 'cachable.short-lived-cache.domain'"))
}


class RasShortLivedCache @Inject()(val shortLiveCache: RasShortLivedHttpCaching, val configuration: Configuration) extends ShortLivedCache {
  override implicit lazy val crypto = new ApplicationCrypto(configuration.underlying).JsonCrypto
}

class RasSessionCache @Inject()(
                                 val environment: Environment,
                                 val appNameConfiguration: Configuration,
                                 val appContext: AppContext,
                                 val http: DefaultHttpClient
                               ) extends SessionCache {
  val mode: Mode.Mode = environment.mode
  override lazy val defaultSource: String = appContext.appName
  override lazy val baseUri: String = appContext.servicesConfig.baseUrl("cachable.session-cache")
  override lazy val domain: String =
    appContext.servicesConfig.getConfString("cachable.session-cache.domain", throw new Exception(s"Could not find config 'cachable.session-cache.domain'"))
}

class RasErrorHandler @Inject()(
                              configuration: Configuration,
                              auditConnector: AuditConnector,
                              httpAuditEvent: HttpAuditEvent,
                              implicit val ec: ExecutionContext
                            ) extends JsonErrorHandler(auditConnector, httpAuditEvent, configuration) {

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = super.onClientError(request, statusCode, message).map(
    result => statusCode match {
      case NOT_FOUND =>
        Logger.info("[RasErrorHandler] [onClientError] MicroserviceGlobal onHandlerNotFound called")
        NotFound(Json.toJson(ErrorNotFound))
      case BAD_REQUEST =>
        Logger.info("[RasErrorHandler] [onClientError] MicroserviceGlobal onBadRequest called")
        BadRequest(Json.toJson(BadRequestResponse))
      case _ => result
    }
  )

  override def onServerError(request: RequestHeader, ex: Throwable): Future[Result] = {
    Logger.info("[RasErrorHandler] [onServerError] MicroserviceGlobal onError called")
    super.onServerError(request, ex) map (res => {
      res.header.status match {
        case UNAUTHORIZED => Unauthorized(Json.toJson(Unauthorised))
        case _ => InternalServerError(Json.toJson(ErrorInternalServerError))
      }
    })
  }
}
