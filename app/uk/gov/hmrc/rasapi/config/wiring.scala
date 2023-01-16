/*
 * Copyright 2023 HM Revenue & Customs
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

import play.api.http.Status.{BAD_REQUEST, NOT_FOUND, UNAUTHORIZED}
import play.api.libs.json.{JsValue, Json, Writes}
import play.api.mvc.Results.{BadRequest, InternalServerError, NotFound, Unauthorized}
import play.api.mvc.{RequestHeader, Result}
import play.api.{Configuration, Logging}
import uk.gov.hmrc.api.controllers.{ErrorInternalServerError, ErrorResponse => ErrorResponseHmrcApi}
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.bootstrap.backend.http.JsonErrorHandler
import uk.gov.hmrc.play.bootstrap.config.HttpAuditEvent
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.rasapi.controllers.{BadRequestResponse, ErrorNotFound, ErrorResponse, Unauthorised}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class RasShortLivedHttpCaching @Inject()(
                                          val appContext: AppContext,
                                          val http: DefaultHttpClient
                                        ) extends ShortLivedHttpCaching {
  override lazy val defaultSource: String = appContext.appName
  override lazy val baseUri: String = appContext.servicesConfig.baseUrl("cachable.short-lived-cache")
  override lazy val domain: String = appContext.servicesConfig.getString("microservice.services.cachable.short-lived-cache.domain")
}


class RasErrorHandler @Inject()(configuration: Configuration,
                                auditConnector: AuditConnector,
                                httpAuditEvent: HttpAuditEvent,
                                implicit val ec: ExecutionContext
                            ) extends JsonErrorHandler(auditConnector, httpAuditEvent, configuration) with Logging {

  implicit val errorResponseWrites: Writes[ErrorResponse] = new Writes[ErrorResponse] {
    def writes(e: ErrorResponse): JsValue = Json.obj("code" -> e.errorCode, "message" -> e.message)
  }

  override def onClientError(request: RequestHeader, statusCode: Int, message: String): Future[Result] = super.onClientError(request, statusCode, message).map(
    result => statusCode match {
      case NOT_FOUND =>
        logger.info("[RasErrorHandler] [onClientError] MicroserviceGlobal onHandlerNotFound called")
        NotFound(errorResponseWrites.writes(ErrorNotFound))
      case BAD_REQUEST =>
        logger.info("[RasErrorHandler] [onClientError] MicroserviceGlobal onBadRequest called")
        BadRequest(errorResponseWrites.writes(BadRequestResponse))
      case _ => result
    }
  )

  override def onServerError(request: RequestHeader, ex: Throwable): Future[Result] = {
    logger.info("[RasErrorHandler] [onServerError] MicroserviceGlobal onError called")
    super.onServerError(request, ex) map (res => {
      res.header.status match {
        case UNAUTHORIZED => Unauthorized(errorResponseWrites.writes(Unauthorised))
        case _ => InternalServerError(ErrorResponseHmrcApi.writes.writes(ErrorInternalServerError))
      }
    })
  }
}
