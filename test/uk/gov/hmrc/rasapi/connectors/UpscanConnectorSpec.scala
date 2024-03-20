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

package uk.gov.hmrc.rasapi.connectors

import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, RequestTimeoutException}
import uk.gov.hmrc.rasapi.config.{AppContext, WSHttp}
import uk.gov.hmrc.rasapi.models.FileMetadata

import java.io.{BufferedReader, InputStreamReader}
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class UpscanConnectorSpec extends AnyWordSpecLike with Matchers with GuiceOneAppPerSuite with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockWsHttp: WSHttp = mock[WSHttp]
  val appContext: AppContext = app.injector.instanceOf[AppContext]
  val wsResponseMock: WSResponse = mock[WSResponse]

  val bodyAsSource: Source[ByteString, _] = Source(Seq(ByteString("Test"),  ByteString("\r\n"), ByteString("Passed")))
  val headers = Map("CONTENT_TYPE" -> Seq("application/octet-stream"))

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

  object TestConnector extends UpscanConnector(mockWsHttp, appContext, ExecutionContext.global)

  val envelopeId: String = "0b215e97-11d4-4006-91db-c067e74fc653"
  val fileId: String = "file-id-1"
  val userId: String = "A1234567"
  val originalFileName: String = "origFileName"
  val dateCreated: String = "2018-01-01T00:00:00Z"
  val fileMetadata: FileMetadata = FileMetadata(fileId, Some(originalFileName), Some(dateCreated))


  "getUpscanFile" should {

    val wsResponse: WSResponse = createMockResponse(bodyAsSource, headers)

    "return an StreamedResponse from Upscan service" in {
      when(mockWsHttp.buildRequestWithStream(any())).thenReturn(Future.successful(wsResponse))

      val result = await(TestConnector.getUpscanFile(envelopeId, fileId, userId))
      val reader = new BufferedReader(new InputStreamReader(result.get))

      (Iterator continually reader.readLine takeWhile (_ != null) toList) should contain theSameElementsAs List("Test", "Passed")
    }
    "return a RuntimeException when Upscan throws error" in {
      when(mockWsHttp.buildRequestWithStream(any())).thenReturn(Future.failed(new RequestTimeoutException("")))

      val exception = intercept[RuntimeException] {
        await(TestConnector.getUpscanFile(envelopeId, fileId, userId))
      }

      exception.getMessage.contains("Error Streaming file from Upscan service") shouldBe true
    }
  }
}
