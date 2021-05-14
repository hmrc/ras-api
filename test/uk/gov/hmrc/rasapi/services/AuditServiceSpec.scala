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

package uk.gov.hmrc.rasapi.services

import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Configuration
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.mongo.Awaiting
import uk.gov.hmrc.play.audit.http.connector.AuditConnector
import uk.gov.hmrc.play.audit.model.DataEvent
import uk.gov.hmrc.rasapi.config.AppContext

import scala.concurrent.ExecutionContext

trait AuditServiceSpec extends WordSpecLike with Matchers with Awaiting with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfter {

  implicit val hc: HeaderCarrier = HeaderCarrier()
  val mockAuditConnector: AuditConnector = mock[AuditConnector]
  val mockAppContext: AppContext = app.injector.instanceOf[AppContext]

  val SUT = new AuditService(mockAuditConnector, app.injector.instanceOf[Configuration], mockAppContext, ExecutionContext.global)

  before {
    reset(mockAuditConnector)
  }

  "AuditService" should {

    val fakeAuditType = "fake-audit-type"
    val fakeEndpoint = "/fake-endpoint"

    val auditDataMap: Map[String, String] = Map("testKey" -> "testValue")

    "build an audit event with the correct mandatory details" in {

      SUT.audit(fakeAuditType, fakeEndpoint, auditDataMap)
      val captor: ArgumentCaptor[DataEvent] = ArgumentCaptor.forClass(classOf[DataEvent])

      verify(mockAuditConnector).sendEvent(captor.capture())(any(), any())

      val event = captor.getValue

      event.auditSource shouldBe "ras-api"
      event.auditType shouldBe "fake-audit-type"
    }

    "build an audit event with the correct tags" in {

      SUT.audit(fakeAuditType, fakeEndpoint, auditDataMap)
      val captor: ArgumentCaptor[DataEvent] = ArgumentCaptor.forClass(classOf[DataEvent])

      verify(mockAuditConnector).sendEvent(captor.capture())(any(), any())

      val event = captor.getValue

      event.tags should contain ("transactionName" -> "fake-audit-type")
      event.tags should contain("path" -> "/fake-endpoint")
      event.tags should contain key "clientIP"
    }

    "build an audit event with the correct detail" in {

      SUT.audit(fakeAuditType, fakeEndpoint, auditDataMap)
      val captor: ArgumentCaptor[DataEvent] = ArgumentCaptor.forClass(classOf[DataEvent])

      verify(mockAuditConnector).sendEvent(captor.capture())(any(), any())

      val event = captor.getValue

      event.detail should contain ("testKey" -> "testValue")

      event.detail should contain key "Authorization"
    }

    "send an event via the audit connector" in {

      SUT.audit(fakeAuditType, fakeEndpoint, auditDataMap)
      verify(mockAuditConnector).sendEvent(any())(any(), any())
    }
  }
}
