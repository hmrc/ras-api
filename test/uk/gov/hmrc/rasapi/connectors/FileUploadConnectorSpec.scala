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

package uk.gov.hmrc.rasapi.connectors

import java.io.{BufferedReader, InputStreamReader}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import org.mockito.ArgumentMatchers._
import org.mockito.Mockito._
import org.mockito.invocation.InvocationOnMock
import org.mockito.stubbing.Answer
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.Json
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse, RequestTimeoutException}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.rasapi.config.{AppContext, WSHttp}
import uk.gov.hmrc.rasapi.models.FileMetadata
import uk.gov.hmrc.rasapi.services.RASWsHelpers

import scala.concurrent.{ExecutionContext, Future}

class FileUploadConnectorSpec extends UnitSpec with RASWsHelpers with GuiceOneAppPerSuite with MockitoSugar {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockWsHttp: WSHttp = mock[WSHttp]
  val appContext: AppContext = app.injector.instanceOf[AppContext]
  val wsResponseMock = mock[WSResponse]

  val bodyAsSource: Source[ByteString, _] = Source(Seq(ByteString("Test"),  ByteString("\r\n"), ByteString("Passed")).to[scala.collection.immutable.Iterable])
  val headers = Map("CONTENT_TYPE" -> Seq("application/octet-stream"))


  private def createMockResponse(status: Int, bodyAsSource: Source[ByteString, _], headers: Map[String, Seq[String]]): WSResponse = {
    when(wsResponseMock.status).thenReturn(status)
    when(wsResponseMock.bodyAsSource).thenAnswer(
      new Answer[Source[ByteString, _]] {
        override def answer(invocation: InvocationOnMock): Source[ByteString, _] = bodyAsSource
      }
    )
    when(wsResponseMock.headers).thenReturn(headers)
    wsResponseMock
  }

  object TestConnector extends FileUploadConnector(mockWsHttp, appContext, ExecutionContext.global)

  val envelopeId: String = "0b215e97-11d4-4006-91db-c067e74fc653"
  val fileId: String = "file-id-1"
  val userId: String = "A1234567"
  val originalFileName: String = "origFileName"
  val dateCreated: String = "2018-01-01T00:00:00Z"
  val fileMetadata: FileMetadata = FileMetadata(fileId, Some(originalFileName), Some(dateCreated))


  "getFile" should {

    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    val wsResponse: WSResponse = createMockResponse(200, bodyAsSource, headers)

    "return an StreamedResponse from File-Upload service" in {
      when(mockWsHttp.buildRequestWithStream(any())(any())).thenReturn(Future.successful(wsResponse))

      val values = List("Test", "Passed")
      val result = await(TestConnector.getFile(envelopeId, fileId, userId))
      val reader = new BufferedReader(new InputStreamReader(result.get))

      (Iterator continually reader.readLine takeWhile (_ != null) toList) should contain theSameElementsAs List("Test", "Passed")
    }
    "return a RuntimeException when File Upload throws error" in {
      when(mockWsHttp.buildRequestWithStream(any())(any())).thenReturn(Future.failed(new RequestTimeoutException("")))

      val exception = intercept[RuntimeException] {
        await(TestConnector.getFile(envelopeId, fileId, userId))
      }

      exception.getMessage.contains("Error Streaming file from file-upload service") shouldBe true
    }
  }

  "getFileMetadata" should {
    "return the original filename when file metadata returns 200" in {
      when(mockWsHttp.doGet(any(), any())(any(), any())).thenReturn(Future.successful(HttpResponse(200, Some(Json.toJson(fileMetadata)))))
      val result = await(TestConnector.getFileMetadata(envelopeId, fileId, userId))
      result.get shouldBe fileMetadata
    }
    "return None when file metadata does not return 200" in {
      when(mockWsHttp.doGet(any(), any())(any(), any())).thenReturn(Future.successful(HttpResponse(404)))
      val result = await(TestConnector.getFileMetadata(envelopeId, fileId, userId))
      result shouldBe None
    }
    "return None when file metadata return an exception" in {
      when(mockWsHttp.doGet(any(), any())(any(), any())).thenReturn(Future.failed(new RequestTimeoutException("")))
      val result = await(TestConnector.getFileMetadata(envelopeId, fileId, userId))
      result shouldBe None
    }
  }

  "deleteUploadedFile" should {
    "submit delete request to file-upload service" in {
      when(mockWsHttp.doDelete(any(), any())(any(), any())).thenReturn(Future.successful(HttpResponse(200)))
      val result = await(TestConnector.deleteUploadedFile(envelopeId, fileId, userId))
      result shouldBe true
    }
    "failed delete request to file-upload service" in {
      when(mockWsHttp.doDelete(any(), any())(any(), any())).thenReturn(Future.successful(HttpResponse(400)))
      val result = await(TestConnector.deleteUploadedFile(envelopeId, fileId, userId))
      result shouldBe false
    }
    "return false when delete returns an exception" in {
      when(mockWsHttp.doDelete(any(), any())(any(), any())).thenReturn(Future.failed(new RequestTimeoutException("")))
      val result = await(TestConnector.deleteUploadedFile(envelopeId, fileId, userId))
      result shouldBe false
    }
  }
}
