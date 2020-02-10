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

import com.typesafe.config.Config
import play.api._
import uk.gov.hmrc.play.config.{AppName, ControllerConfig, RunMode, ServicesConfig}
import uk.gov.hmrc.play.microservice.bootstrap.DefaultMicroserviceGlobal
import net.ceedubs.ficus.Ficus._
import play.api.Mode.Mode
import play.api.libs.json.Json
import play.api.libs.json.Json.toJson
import play.api.mvc.{RequestHeader, Result}
import play.api.mvc.Results.{BadRequest, NotFound, Status}
import uk.gov.hmrc.crypto.ApplicationCrypto
import uk.gov.hmrc.rasapi.controllers.{BadRequestResponse, ErrorInternalServerError, ErrorNotFound, Unauthorised}
import uk.gov.hmrc.http.cache.client.{SessionCache, ShortLivedCache, ShortLivedHttpCaching}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.microservice.filters.{AuditFilter, LoggingFilter, MicroserviceFilterSupport}
import uk.gov.hmrc.rasapi.services.DataCleansingService

trait DataCleansing extends GlobalSettings with RunMode {

  implicit val hc: HeaderCarrier


  override def onStart(app: Application): Unit = {
    super.onStart(app)

    AppContext.removeChunksDataExerciseEnabled match {
      case true => {
        Logger.info("[data-cleansing-exercise] [on-start] Starting data exercise for removing of chunks")
        DataCleansingService.removeOrphanedChunks()
      }
      case false => Logger.warn("[data-cleansing-exercise] [on-start] No data exercise carried")

    }
  }
}

object ControllerConfiguration extends ControllerConfig {
  lazy val controllerConfigs = Play.current.configuration.underlying.as[Config]("controllers")
}

object MicroserviceAuditFilter extends AuditFilter with AppName with MicroserviceFilterSupport {
  override val auditConnector = MicroserviceAuditConnector
  override def controllerNeedsAuditing(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsAuditing

  override protected def appNameConfiguration: Configuration = Play.current.configuration
}

object MicroserviceLoggingFilter extends LoggingFilter with MicroserviceFilterSupport {
  override def controllerNeedsLogging(controllerName: String) = ControllerConfiguration.paramsForController(controllerName).needsLogging
}

object MicroserviceGlobal extends DefaultMicroserviceGlobal with RunMode with MicroserviceFilterSupport with DataCleansing  {
  override val auditConnector = MicroserviceAuditConnector

  override def microserviceMetricsConfig(implicit app: Application): Option[Configuration] = app.configuration.getConfig(s"microservice.metrics")

  override val loggingFilter = MicroserviceLoggingFilter

  override val microserviceAuditFilter = MicroserviceAuditFilter

  override val authFilter = None

  override implicit val hc: HeaderCarrier = HeaderCarrier()

  override protected def mode: Mode = Play.current.mode

  override protected def runModeConfiguration: Configuration = Play.current.configuration

  override def onBadRequest(request: RequestHeader, error: String) = {

    Future.successful {
      Logger.info("MicroserviceGlobal onBadRequest called")
      BadRequest(toJson(BadRequestResponse))
    }
  }

  override def onHandlerNotFound(request: RequestHeader) = {

    Future.successful {
      Logger.info("MicroserviceGlobal onHandlerNotFound called")
      NotFound(toJson(ErrorNotFound))
    }
  }

  override def onError(request: RequestHeader, ex: Throwable): Future[Result] = {

    Logger.info("MicroserviceGlobal onError called")

    super.onError(request, ex) map (res => {
      res.header.status match {
        case 401 => Status(Unauthorised.httpStatusCode)(Json.toJson(Unauthorised))
        case _ => Status(ErrorInternalServerError.httpStatusCode)(toJson(ErrorInternalServerError))
      }
    })
  }
}

object RasShortLivedHttpCaching extends ShortLivedHttpCaching with AppName with ServicesConfig {
  override lazy val http = WSHttp
  override lazy val defaultSource = appName
  override lazy val baseUri = baseUrl("cachable.short-lived-cache")
  override lazy val domain = getConfString("cachable.short-lived-cache.domain", throw new Exception(s"Could not find config 'cachable.short-lived-cache.domain'"))

  override protected def appNameConfiguration: Configuration = Play.current.configuration
  override protected def mode: Mode = Play.current.mode
  override protected def runModeConfiguration: Configuration = Play.current.configuration
}

object RasShortLivedCache extends ShortLivedCache {
  override implicit lazy val crypto = new ApplicationCrypto(Play.current.configuration.underlying).JsonCrypto
  override lazy val shortLiveCache = RasShortLivedHttpCaching
}

object RasSessionCache extends SessionCache with AppName with ServicesConfig {
  override lazy val http = WSHttp
  override lazy val defaultSource = appName
  override lazy val baseUri = baseUrl("cachable.session-cache")
  override lazy val domain = getConfString("cachable.session-cache.domain", throw new Exception(s"Could not find config 'cachable.session-cache.domain'"))

  override protected def appNameConfiguration: Configuration = Play.current.configuration
  override protected def mode: Mode = Play.current.mode
  override protected def runModeConfiguration: Configuration = Play.current.configuration
}