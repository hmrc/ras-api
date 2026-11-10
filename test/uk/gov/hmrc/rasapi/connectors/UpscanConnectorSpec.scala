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
import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.*
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{HeaderCarrier, RequestId, RequestTimeoutException}
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import uk.gov.hmrc.rasapi.config.{AppContext, WSHttp}
import uk.gov.hmrc.rasapi.models.FileMetadata
import uk.gov.hmrc.rasapi.services.AuditService

import java.io.{BufferedReader, InputStreamReader}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.*
import scala.language.postfixOps

class UpscanConnectorSpec
    extends AnyWordSpecLike with Matchers with GuiceOneAppPerSuite with MockitoSugar with LogCapturing {

  given hc: HeaderCarrier = HeaderCarrier()

  val mockWsHttp: WSHttp             = mock[WSHttp]
  val mockAuditService: AuditService = mock[AuditService]
  val appContext: AppContext         = app.injector.instanceOf[AppContext]
  val wsResponseMock: WSResponse     = mock[WSResponse]

  val bodyAsSource: Source[ByteString, _] = Source(Seq(ByteString("Test"), ByteString("\r\n"), ByteString("Passed")))
  val headers                             = Map("CONTENT_TYPE" -> Seq("application/octet-stream"))

  private def createMockResponse(bodyAsSource: Source[ByteString, _], headers: Map[String, Seq[String]]): WSResponse = {
    when(wsResponseMock.status).thenReturn(200)
    when(wsResponseMock.bodyAsSource).thenAnswer(
      new Answer[Source[ByteString, _]] {
        override def answer(invocation: InvocationOnMock): Source[ByteString, _] = bodyAsSource
      }
    )
    when(wsResponseMock.headers).thenReturn(headers)
    wsResponseMock
  }

  val uuid = "123e4567-e89b-42d3-a456-556642440000"

  object TestConnector extends UpscanConnector(mockWsHttp, mockAuditService, appContext) {
    override def generateNewUUID: String = uuid

    def loggerForTest: play.api.Logger = logger
  }

  val requestId                      = "abcd6789-dh12-fg34-ij56"
  val expectedCorrId                 = s"$requestId-${uuid.substring(24)}"
  val hcWithRequestId: HeaderCarrier = HeaderCarrier(requestId = Some(RequestId(requestId)))

  def logMessagesWithLevel(logs: List[ILoggingEvent]): List[(Level, String)] =
    logs.map(event => event.getLevel -> event.getMessage)

  def upscanAudit(): Map[String, String] = {
    val typeCaptor                                      = ArgumentCaptor.forClass(classOf[String])
    val pathCaptor                                      = ArgumentCaptor.forClass(classOf[String])
    val dataCaptor: ArgumentCaptor[Map[String, String]] = ArgumentCaptor.forClass(classOf[Map[String, String]])
    verify(mockAuditService, atLeastOnce())
      .audit(typeCaptor.capture(), pathCaptor.capture(), dataCaptor.capture())(using any[HeaderCarrier])
    dataCaptor.getAllValues.asScala.toList.last
  }

  val reference: String          = "0b215e97-11d4-4006-91db-c067e74fc653"
  val fileId: String             = "file-id-1"
  val userId: String             = "A1234567"
  val originalFileName: String   = "origFileName"
  val dateCreated: String        = "2018-01-01T00:00:00Z"
  val fileMetadata: FileMetadata = FileMetadata(fileId, Some(originalFileName), Some(dateCreated))

  "getUpscanFile" should {

    val wsResponse: WSResponse = createMockResponse(bodyAsSource, headers)

    "return an StreamedResponse from Upscan service" in {
      when(mockWsHttp.buildRequestWithStream(any())).thenReturn(Future.successful(wsResponse))

      val result = await(TestConnector.getUpscanFile(reference, fileId, userId))
      val reader = new BufferedReader(new InputStreamReader(result.get))

      (Iterator continually reader.readLine takeWhile (_ != null) toList) should contain theSameElementsAs List(
        "Test",
        "Passed"
      )
    }
    "return a RuntimeException when Upscan throws error" in {
      when(mockWsHttp.buildRequestWithStream(any())).thenReturn(Future.failed(new RequestTimeoutException("")))

      val exception = intercept[RuntimeException] {
        await(TestConnector.getUpscanFile(reference, fileId, userId))
      }

      exception.getMessage.contains("Error Streaming file from Upscan service") shouldBe true
    }
  }

  "UpscanConnector logging" should {

    "log the correlation id at INFO level when retrieving a file" in {
      // Arrange
      val wsResponse = createMockResponse(bodyAsSource, headers)
      when(mockWsHttp.buildRequestWithStream(any())).thenReturn(Future.successful(wsResponse))

      withCaptureOfLoggingFrom(TestConnector.loggerForTest) { logs =>
        // Act
        await(TestConnector.getUpscanFile(reference, fileId, userId)(using hcWithRequestId))

        // Assert
        logMessagesWithLevel(logs) should contain(
          Level.INFO -> s"[UpscanConnector][getUpscanFile] Trying to retrieve file from Upscan: $fileId for userId ($userId) correlationId ($expectedCorrId)."
        )
      }
    }

    "log the correlation id at ERROR level when retrieval fails" in {
      // Arrange
      when(mockWsHttp.buildRequestWithStream(any())).thenReturn(Future.failed(new RequestTimeoutException("timed out")))

      withCaptureOfLoggingFrom(TestConnector.loggerForTest) { logs =>
        // Act
        intercept[RuntimeException](
          await(TestConnector.getUpscanFile(reference, fileId, userId)(using hcWithRequestId))
        )

        // Assert
        logMessagesWithLevel(logs)
          .collect { case (Level.ERROR, message) => message }
          .exists(_.contains(s"correlationId ($expectedCorrId)")) shouldBe true
      }
    }
  }

  "UpscanConnector auditing" should {

    "audit a successful file retrieval with correlation id, user id and outcome" in {
      // Arrange
      reset(mockAuditService)
      val wsResponse = createMockResponse(bodyAsSource, headers)
      when(mockWsHttp.buildRequestWithStream(any())).thenReturn(Future.successful(wsResponse))

      // Act
      await(TestConnector.getUpscanFile(reference, fileId, userId)(using hcWithRequestId))

      // Assert
      val detail = upscanAudit()
      detail("downstreamService") shouldBe "UPSCAN"
      detail("outcome")           shouldBe "SUCCESS"
      detail("userIdentifier")    shouldBe userId
      detail("fileReference")     shouldBe fileId
      detail("correlationId")     shouldBe expectedCorrId
      detail("processingPath")    shouldBe "UpscanConnector.getUpscanFile"
    }

    "audit a failed file retrieval with failure category, error detail and 'recover' processing path" in {
      // Arrange
      reset(mockAuditService)
      when(mockWsHttp.buildRequestWithStream(any())).thenReturn(Future.failed(new RequestTimeoutException("timed out")))

      // Act
      intercept[RuntimeException](await(TestConnector.getUpscanFile(reference, fileId, userId)(using hcWithRequestId)))

      // Assert
      val detail = upscanAudit()
      detail("outcome")         shouldBe "FAILURE"
      detail("failureCategory") shouldBe "STREAMING_ERROR"
      detail("errorDetail")     shouldBe "timed out"
      detail("processingPath")    should endWith("recover")
    }
  }

}
