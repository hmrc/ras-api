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

package uk.gov.hmrc.rasapi.modules


import play.api.inject.{Binding, Module}
import play.api.{Configuration, Environment}
import uk.gov.hmrc.http.cache.client.ShortLivedHttpCaching
import uk.gov.hmrc.rasapi.config.{AppContext, RasShortLivedHttpCaching, WSHttp}
import uk.gov.hmrc.rasapi.connectors.{DesConnector, FileUploadConnector}
import uk.gov.hmrc.rasapi.controllers.{Documentation, FileController, FileProcessingController, LookupController}
import uk.gov.hmrc.rasapi.helpers.ResidencyYearResolver
import uk.gov.hmrc.rasapi.metrics.Metrics
import uk.gov.hmrc.rasapi.repository.{RasChunksRepository, RasFilesRepository}
import uk.gov.hmrc.rasapi.services.{AuditService, DataCleansingService, FileProcessingService, SessionCacheService}
import uk.gov.hmrc.rasapi.utils.ErrorConverter

class APiModule extends Module {

  lazy val bindControllers: Seq[Binding[_]] = Seq(
    bind[Documentation].toSelf.eagerly(),
    bind[FileController].toSelf.eagerly(),
    bind[FileProcessingController].toSelf.eagerly(),
    bind[LookupController].toSelf.eagerly()
  )

  lazy val bindServices: Seq[Binding[_]] = Seq(
    bind[DataCleansingService].toSelf.eagerly(),
    bind[AuditService].toSelf.eagerly(),
    bind[FileProcessingService].toSelf.eagerly(),
    bind[SessionCacheService].toSelf.eagerly()
  )

  lazy val bindConnectors: Seq[Binding[_]] = Seq(
    bind[DesConnector].toSelf.eagerly(),
    bind[FileUploadConnector].toSelf.eagerly()
  )

  lazy val bindRepositories: Seq[Binding[_]] = Seq(
    bind[RasChunksRepository].toSelf.eagerly(),
    bind[RasFilesRepository].toSelf.eagerly()
  )

  lazy val bindUtils: Seq[Binding[_]] = Seq(
    bind[AppContext].toSelf.eagerly(),
    bind[Metrics].toSelf.eagerly(),
    bind[ErrorConverter].toSelf.eagerly(),
    bind[ShortLivedHttpCaching].to[RasShortLivedHttpCaching].eagerly(),
    bind[ResidencyYearResolver].toSelf.eagerly(),
    bind[WSHttp].toSelf.eagerly()
  )

  override def bindings(environment: Environment, configuration: Configuration): Seq[Binding[_]] =
    bindControllers ++ bindServices ++ bindConnectors ++ bindRepositories ++ bindUtils
}
