/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.rasapi.connectors

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, urlEqualTo}
import com.github.tomakehurst.wiremock.http.Fault
import org.apache.pekko.stream.scaladsl.Source as PekkoSource
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.{any, eq as eqTo}
import org.mockito.Mockito.{reset, verify}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.bind
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import play.api.{Application, Logger}
import uk.gov.hmrc.http.{HeaderCarrier, RequestId}
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import uk.gov.hmrc.rasapi.models.{ConnectorFailure, FailureReason}
import uk.gov.hmrc.rasapi.services.AuditService
import uk.gov.hmrc.rasapi.utils.WireMockHelper

import java.io.{IOException, InputStream}
import scala.io.Source
import scala.util.Using

class UpscanConnectorSpec
    extends AnyWordSpecLike
    with Matchers
    with GuiceOneAppPerSuite
    with WireMockHelper
    with LogCapturing
    with Eventually
    with IntegrationPatience
    with MockitoSugar
    with BeforeAndAfterEach {

  val mockAuditService: AuditService = mock[AuditService]

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure("play.ws.timeout.idle" -> "500 millis") // short timeout, so timeout test fails fast
      .overrides(bind[AuditService].toInstance(mockAuditService))
      .build()

  override def beforeEach(): Unit = {
    super.beforeEach()
    reset(mockAuditService)
  }

  given hc: HeaderCarrier             = HeaderCarrier()
  lazy val connector: UpscanConnector = app.injector.instanceOf[UpscanConnector]

  val reference: String = "0b215e97-11d4-4006-91db-c067e74fc653"
  val userId: String    = "A1234567"

  val connectorLogger: Logger = Logger(classOf[UpscanConnector])

  private val commonAuditData: Map[String, String] = Map(
    "downstreamService" -> "UPSCAN",
    "fileReference"     -> reference,
    "userIdentifier"    -> userId,
    "auditEventType"    -> "ReliefAtSourceUpscanFileRetrieval"
  )

  private def downloadUrl: String = s"http://localhost:${server.port()}/download"

  private def getFile()(using HeaderCarrier): Option[InputStream] =
    await(connector.getUpscanFile(downloadUrl, reference, userId))

  private def stubDownload(status: Int, body: String): Unit =
    server.stubFor(
      get(urlEqualTo("/download"))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/octet-stream")
            .withBody(body)
        )
    )

  private def stubFault(fault: Fault): Unit =
    server.stubFor(get(urlEqualTo("/download")).willReturn(aResponse().withFault(fault)))

  private def readAll(stream: InputStream): Array[Byte] =
    Using.resource(stream)(_.readAllBytes())

  private def readLines(stream: InputStream): List[String] =
    Using.resource(Source.fromInputStream(stream))(_.getLines().toList)

  private def messagesAt(level: Level, logs: List[ILoggingEvent]): List[String] =
    logs.filter(_.getLevel == level).map(_.getFormattedMessage)

  private def auditedData(): Map[String, String] = {
    val captor = ArgumentCaptor.forClass(classOf[Map[String, String]])
    verify(mockAuditService).audit(eqTo("ReliefAtSourceUpscanFileRetrieval"), eqTo(reference), captor.capture())(using
      any[HeaderCarrier]()
    )
    captor.getValue
  }

  "getUpscanFile" should {

    "return the file as a stream, and log and audit SUCCESS once it has been read, when Upscan responds 200" in {
      stubDownload(200, "Test\r\nPassed")

      withCaptureOfLoggingFrom(connectorLogger) { logs =>
        readLines(getFile().get) shouldBe List("Test", "Passed")

        eventually {
          messagesAt(Level.INFO, logs).exists(_.contains(s"outcome=SUCCESS userId=$userId")) shouldBe true
        }
        messagesAt(Level.ERROR, logs) shouldBe empty
      }

      val audit = eventually(auditedData())
      audit                                          should contain allElementsOf commonAuditData
      audit                                          should contain allElementsOf Map(
        "outcome"        -> "SUCCESS",
        "processingPath" -> "UpscanConnector.getUpscanFile"
      )
      audit.keySet                                   should contain noneOf ("reason", "httpStatus", "failureCategory", "errorDetail")
      audit.values.exists(_.contains("/download")) shouldBe false // the download URL is never audited
    }

    "return None, and log and audit the status, when Upscan responds 404" in {
      stubDownload(404, "not found")

      withCaptureOfLoggingFrom(connectorLogger) { logs =>
        getFile() shouldBe None

        val errorLogs = messagesAt(Level.ERROR, logs)

        errorLogs      should have size 1
        errorLogs.head should include("outcome=FAILURE reason=UNEXPECTED_STATUS")
        errorLogs.head should include("status=404")
      }

      val audit = auditedData()
      audit should contain allElementsOf commonAuditData
      audit should contain allElementsOf Map(
        "outcome"         -> "FAILURE",
        "reason"          -> "UNEXPECTED_STATUS",
        "httpStatus"      -> "404",
        "failureCategory" -> "HTTP_404",
        "processingPath"  -> "UpscanConnector.getUpscanFile.unexpectedStatus"
      )
    }

    "fail with UNEXPECTED_STATUS, and log and audit the status, when Upscan responds with any other error status" in {
      stubDownload(503, "unavailable")

      withCaptureOfLoggingFrom(connectorLogger) { logs =>
        val failure = intercept[ConnectorFailure](getFile())

        failure.reason shouldBe FailureReason.UNEXPECTED_STATUS
        failure.status shouldBe Some(503)

        val errorLogs = messagesAt(Level.ERROR, logs)

        errorLogs      should have size 1
        errorLogs.head should include("outcome=FAILURE reason=UNEXPECTED_STATUS")
        errorLogs.head should include("status=503")
        errorLogs.head should not include "/download"
      }

      val audit = auditedData()
      audit should contain allElementsOf commonAuditData

      audit should contain allElementsOf Map(
        "outcome"         -> "FAILURE",
        "reason"          -> "UNEXPECTED_STATUS",
        "httpStatus"      -> "503",
        "failureCategory" -> "HTTP_503",
        "processingPath"  -> "UpscanConnector.getUpscanFile.unexpectedStatus"
      )
    }

    "fail with CONNECTION_FAILURE, and log and audit userId and correlationId, when the connection to Upscan fails" in {
      stubFault(Fault.CONNECTION_RESET_BY_PEER)

      val requestId: String                = "abcd0000-dh12-fg34-ij56"
      given hcWithRequestId: HeaderCarrier = HeaderCarrier(requestId = Some(RequestId(requestId)))

      withCaptureOfLoggingFrom(connectorLogger) { logs =>
        val failure = intercept[ConnectorFailure](getFile())

        failure.reason shouldBe FailureReason.CONNECTION_FAILURE
        failure.cause  shouldBe defined

        val errorLogs = messagesAt(Level.ERROR, logs)
        errorLogs should have size 1

        errorLogs.head should startWith(
          s"[UpscanConnector][getUpscanFile] outcome=FAILURE reason=CONNECTION_FAILURE userId=$userId"
        )

        errorLogs.head should include(s"corrId=$requestId-")
        errorLogs.head should not include "/download"
      }

      val audit = auditedData()
      audit                  should contain allElementsOf commonAuditData
      audit                  should contain allElementsOf Map(
        "outcome"        -> "FAILURE",
        "reason"         -> "CONNECTION_FAILURE",
        "processingPath" -> "UpscanConnector.getUpscanFile.recover"
      )
      audit("correlationId") should startWith(s"$requestId-")
      audit.keySet           should contain("errorDetail")
    }

    "fail with TIMEOUT, and log and audit it, when Upscan doesn't respond within the idle timeout" in {
      server.stubFor(
        get(urlEqualTo("/download"))
          .willReturn(aResponse().withStatus(200).withBody("late").withFixedDelay(2000))
      )

      withCaptureOfLoggingFrom(connectorLogger) { logs =>
        intercept[ConnectorFailure](getFile()).reason shouldBe FailureReason.TIMEOUT

        val errorLogs = messagesAt(Level.ERROR, logs)

        errorLogs      should have size 1
        errorLogs.head should include("outcome=FAILURE reason=TIMEOUT")
        errorLogs.head should not include "/download"
      }

      val audit = auditedData()
      audit should contain allElementsOf commonAuditData

      audit should contain allElementsOf Map(
        "outcome"        -> "FAILURE",
        "reason"         -> "TIMEOUT",
        "processingPath" -> "UpscanConnector.getUpscanFile.recover"
      )
    }
  }

  "monitoredStream" should {

    "log and audit a DOWNLOAD_INCOMPLETE, and no SUCCESS, when the body fails part-way through the download" in {
      // simulates a connection dropped mid-download
      val failingBody =
        PekkoSource.single(ByteString("partial")) ++ PekkoSource.failed(new IOException("Connection reset"))

      withCaptureOfLoggingFrom(connectorLogger) { logs =>
        intercept[IOException] {
          readAll(connector.monitoredStream(failingBody, userId, reference, corrId = "test-corr-id"))
        }

        eventually {
          val errorLogs = messagesAt(Level.ERROR, logs)

          errorLogs      should have size 1
          errorLogs.head should include("outcome=FAILURE reason=DOWNLOAD_INCOMPLETE")
          errorLogs.head should include(s"userId=$userId")
          errorLogs.head should include("corrId=test-corr-id")
        }

        messagesAt(Level.INFO, logs).exists(_.contains("outcome=SUCCESS")) shouldBe false
      }

      val audit = eventually(auditedData())
      audit should contain allElementsOf commonAuditData

      audit should contain allElementsOf Map(
        "outcome"         -> "FAILURE",
        "reason"          -> "DOWNLOAD_INCOMPLETE",
        "failureCategory" -> "STREAMING_ERROR",
        "errorDetail"     -> "Connection reset",
        "correlationId"   -> "test-corr-id",
        "processingPath"  -> "UpscanConnector.getUpscanFile.streamFailure"
      )
    }
  }

}
