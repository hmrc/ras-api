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

package uk.gov.hmrc.rasapi.itUtils

import org.scalatest.BeforeAndAfterAll
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.github.tomakehurst.wiremock.stubbing.StubMapping
import org.scalatestplus.play.PlaySpec
import play.api.test.Helpers.OK

trait WireMockServerHelper extends BeforeAndAfterAll {
  self: PlaySpec =>

  val mockPort = 11111

  lazy val wireMockServer = new WireMockServer(mockPort)

  override protected def beforeAll(): Unit = {
    wireMockServer.start()
    configureFor("localhost", mockPort)
  }

  def authMocks: StubMapping = stubFor(post(urlEqualTo("/auth/authorise"))
    .willReturn(
      aResponse()
        .withStatus(OK)
        .withHeader("Authorization","Bearer 123")
        .withBody(
          """{
            | "authorisedEnrolments": [
            |   {
            |     "key": "HMRC-PSA-ORG",
            |     "identifiers": [{ "key": "PSAID", "value": "A1234567" }],
            |     "state": "Activated"
            |   }
            | ]
            |}""".stripMargin)
    ))

}
