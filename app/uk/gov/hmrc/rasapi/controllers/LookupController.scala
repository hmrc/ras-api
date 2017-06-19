/*
 * Copyright 2017 HM Revenue & Customs
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

import play.api.mvc.{Action, AnyContent}
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.api.controllers.HeaderValidator
import uk.gov.hmrc.play.config.RunMode
import uk.gov.hmrc.rasapi.connectors.{CachingConnector, DESConnector}
import uk.gov.hmrc.rasapi.models.{CustomerDetails, InvalidUUIDForbiddenResponse, ResidencyStatus}
import play.api.libs.json.Json._
import play.api.Logger


import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

trait LookupController extends BaseController with HeaderValidator with RunMode {

  val cachingConnector: CachingConnector
  val desConnector: DESConnector

  def getResidencyStatus(uuid: String): Action[AnyContent] = validateAccept(acceptHeaderValidationRules).async {
    implicit request =>

      val customerDetails: Option[CustomerDetails] = cachingConnector.getCachedData(uuid)
      val residencyStatus: Option[ResidencyStatus] = desConnector.getResidencyStatus(customerDetails.getOrElse(CustomerDetails()))

      if (isValidUUID(uuid))
        Future(Ok(toJson(residencyStatus.getOrElse(ResidencyStatus("","")))))
      else
        Future(Forbidden(toJson(InvalidUUIDForbiddenResponse)))

  }

  private def isValidUUID(uuid: String): Boolean = {

    val uuidRegex = "^((2800a7ab-fe20-42ca-98d7-c33f4133cfc2)|(633e0ee7-315b-49e6-baed-d79c3dffe467)|" +
      "(77648d82-309e-484d-a310-d0ffd2997791)|(79f21755-8cd4-4785-9c10-13253f7a8bb6))$"

    uuid.matches(uuidRegex)
  }
}

object LookupController extends LookupController {
  override val cachingConnector: CachingConnector = CachingConnector
  override val desConnector: DESConnector = DESConnector
}
