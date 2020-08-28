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

package uk.gov.hmrc.rasapi.services

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.rasapi.config.AppContext

import scala.concurrent.ExecutionContext

trait AuditServiceSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfter {

  implicit val hc:HeaderCarrier = HeaderCarrier()
  val mockAuditConnector = mock[AuditConnector]
  val mockAppContext = app.injector.instanceOf[AppContext]

  val SUT = new AuditService(mockAuditConnector, app.injector.instanceOf[Configuration], mockAppContext, ExecutionContext.global)

  before {
    reset(mockAuditConnector)
  }

  "AuditService" should {

    val fakeAuditType = "fake-audit-type"
    val fakeEndpoint = "/fake-endpoint"

    val auditDataMap: Map[String, String] = Map("testKey" -> "testValue")

    "build an audit event with the correct mandatory details" in {

      val result = SUT.audit(fakeAuditType, fakeEndpoint, auditDataMap)
      val captor = ArgumentCaptor.forClass(classOf[DataEvent])

      verify(mockAuditConnector).sendEvent(captor.capture())(any(), any())

      val event = captor.getValue

      event.auditSource shouldBe "ras-api"
      event.auditType shouldBe "fake-audit-type"
    }

    "build an audit event with the correct tags" in {

      val result = SUT.audit(fakeAuditType, fakeEndpoint, auditDataMap)
      val captor = ArgumentCaptor.forClass(classOf[DataEvent])

      verify(mockAuditConnector).sendEvent(captor.capture())(any(), any())

      val event = captor.getValue

      event.tags should contain ("transactionName" -> "fake-audit-type")
      event.tags should contain("path" -> "/fake-endpoint")
      event.tags should contain key "clientIP"
    }

    "build an audit event with the correct detail" in {

      val result = SUT.audit(fakeAuditType, fakeEndpoint, auditDataMap)
      val captor = ArgumentCaptor.forClass(classOf[DataEvent])

      verify(mockAuditConnector).sendEvent(captor.capture())(any(), any())

      val event = captor.getValue

      event.detail should contain ("testKey" -> "testValue")

      event.detail should contain key "Authorization"
    }

    "send an event via the audit connector" in {

      val result = SUT.audit(fakeAuditType, fakeEndpoint, auditDataMap)
      verify(mockAuditConnector).sendEvent(any())(any(), any())
    }
  }
}
