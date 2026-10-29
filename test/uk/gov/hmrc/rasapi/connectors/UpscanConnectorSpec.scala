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

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import play.api.test.Helpers.{await, defaultAwaitTimeout}

import java.io.{BufferedReader, InputStreamReader}

import uk.gov.hmrc.rasapi.utils.WireMockHelper
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, get, getRequestedFor, urlEqualTo}
import com.github.tomakehurst.wiremock.http.Fault

class UpscanConnectorSpec extends AnyWordSpecLike with Matchers with GuiceOneAppPerSuite with WireMockHelper {

  lazy val connector: UpscanConnector = app.injector.instanceOf[UpscanConnector]

  val reference: String = "0b215e97-11d4-4006-91db-c067e74fc653"

  val userId: String = "A1234567"

  "getUpscanFile" should {

    "return a streamed response from Upscan service" in {

      server.stubFor(
        get(urlEqualTo("/download"))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/octet-stream")
              .withBody("Test\r\nPassed")
          )
      )

      val downloadUrl =
        s"http://localhost:${server.port()}/download"

      val result =
        await(connector.getUpscanFile(downloadUrl, reference, userId))

      val reader =
        new BufferedReader(new InputStreamReader(result.get))

      try {
        val lines = Iterator
          .continually(reader.readLine())
          .takeWhile(_ != null)
          .toList

        lines shouldBe List("Test", "Passed")
      } finally reader.close()

      server.verify(
        1,
        getRequestedFor(urlEqualTo("/download"))
      )
    }

    "return a RuntimeException when Upscan connection fails" in {

      server.stubFor(
        get(urlEqualTo("/download"))
          .willReturn(
            aResponse()
              .withFault(Fault.CONNECTION_RESET_BY_PEER)
          )
      )

      val downloadUrl =
        s"http://localhost:${server.port()}/download"

      val exception = intercept[RuntimeException] {
        await(connector.getUpscanFile(downloadUrl, reference, userId))
      }

      exception.getMessage shouldBe
        "Error Streaming file from Upscan service"

      server.verify(
        1,
        getRequestedFor(urlEqualTo("/download"))
      )
    }

  }

}
