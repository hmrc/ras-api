
package uk.gov.hmrc.rasapi.itUtils

import org.scalatest.BeforeAndAfterAll
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
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
