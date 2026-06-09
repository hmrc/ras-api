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

package uk.gov.hmrc.rasapi.services

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.*
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.rasapi.connectors.UpscanConnector

import java.io.{ByteArrayInputStream, FileNotFoundException}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RasFileReaderSpec extends AnyWordSpecLike with Matchers with MockitoSugar {

  def newReader(connector: UpscanConnector): RasFileReader = new RasFileReader {
    val fileUploadConnector: UpscanConnector = connector
  }

  "RasFileReader.readFile" should {

    "return iterator of lines when the upstream provides an InputStream" in {
      val mockConnector = mock[UpscanConnector]
      val reader        = newReader(mockConnector)
      val bytes         = "line1\nline2".getBytes("ISO-8859-1")
      when(mockConnector.getUpscanFile(any(), any(), any()))
        .thenReturn(Future.successful(Some(new ByteArrayInputStream(bytes))))

      val result = await(reader.readFile("url", "ref", "user"))

      result.toList shouldEqual List("line1", "line2")
    }

    "throw FileNotFoundException when the upstream returns None" in {
      val mockConnector = mock[UpscanConnector]
      val reader        = newReader(mockConnector)
      when(mockConnector.getUpscanFile(any(), any(), any())).thenReturn(Future.successful(None))

      intercept[FileNotFoundException] {
        await(reader.readFile("url", "missing-ref", "user"))
      }
    }
  }

}
