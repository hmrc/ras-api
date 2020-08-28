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

package uk.gov.hmrc.rasapi.controllers

import controllers.Assets
import javax.inject.Inject
import play.api.http.LazyHttpErrorHandler
import play.api.mvc.{Action, AnyContent, ControllerComponents}
import uk.gov.hmrc.api.controllers.DocumentationController
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.views.txt


class Documentation @Inject()(appContext: AppContext,
                              cc: ControllerComponents,
                              assets: Assets) extends DocumentationController(cc, assets, LazyHttpErrorHandler) {

  override def documentation(version: String, endpointName: String): Action[AnyContent] = {
    assets.at(s"/public/api/documentation/$version", s"${endpointName.replaceAll(" ", "-")}.xml")
  }

  override def definition(): Action[AnyContent] = Action {
    Ok(txt.definition(appContext.apiContext, appContext.apiStatus, appContext.endpointsEnabled, appContext.apiV2_0Enabled))
  }

  def raml(version: String, file: String): Action[AnyContent] = {
    assets.at(s"/public/api/conf/$version", file)
  }
}
