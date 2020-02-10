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

package uk.gov.hmrc.rasapi.services

import play.api.{Configuration, Play}
import uk.gov.hmrc.rasapi.config.MicroserviceAuditConnector
import uk.gov.hmrc.play.audit.http.connector.{AuditConnector, AuditResult}
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.play.audit.AuditExtensions._
import uk.gov.hmrc.play.config.AppName

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import uk.gov.hmrc.http.HeaderCarrier

trait AuditService extends AppName {
  val connector: AuditConnector
  override protected def appNameConfiguration: Configuration = Play.current.configuration

  def audit(auditType: String, path: String, auditData: Map[String, String])(implicit hc:HeaderCarrier): Future[AuditResult] = {
    val event = DataEvent(
      auditSource = appName,
      auditType = auditType,
      tags = hc.toAuditTags(auditType, path),
      detail = hc.toAuditDetails() ++ auditData
    )

    connector.sendEvent(event)
  }

}

object AuditService extends AuditService {
  // $COVERAGE-OFF$Trivial and never going to be called by a test that uses it's own object implementation
  override val connector: AuditConnector = MicroserviceAuditConnector
  // $COVERAGE-ON$
}
