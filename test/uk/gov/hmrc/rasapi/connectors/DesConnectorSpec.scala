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
import com.github.tomakehurst.wiremock.client.WireMock.{
  aResponse, containing, equalTo, equalToJson, post, postRequestedFor, urlEqualTo
}
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.*
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClientV2Provider
import uk.gov.hmrc.play.bootstrap.tools.LogCapturing
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.models.*
import uk.gov.hmrc.rasapi.services.AuditService
import uk.gov.hmrc.rasapi.utils.WireMockHelper

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}

class DesConnectorSpec
    extends AnyWordSpecLike with Matchers with GuiceOneAppPerSuite with WireMockHelper with LogCapturing {

  given hc: HeaderCarrier                       = HeaderCarrier()
  given format: OFormat[ResidencyStatusSuccess] = ResidencyStatusFormats.successFormats

  val uuid                         = "123e4567-e89b-42d3-a456-556642440000"
  val userId                       = "A123456"
  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  val servicesConfig: ServicesConfig      = app.injector.instanceOf[ServicesConfig]
  val appContext: AppContext              = app.injector.instanceOf[AppContext]
  lazy val realHttp: HttpClientV2Provider = app.injector.instanceOf[HttpClientV2Provider]

  object TestDesConnector
      extends DesConnector(realHttp, app.injector.instanceOf[AuditService], appContext)(using ExecutionContext.global) {
    override lazy val desBaseUrl: String             = s"http://localhost:${server.port()}"
    override lazy val allowNoNextYearStatus: Boolean = true
    override lazy val retryLimit: Int                = 3
    override lazy val retryDelay: Int                = 500
    override lazy val desUrlHeaderEnv: String        = "DES HEADER"
    override lazy val desAuthToken: String           = "DES AUTH TOKEN"
    override lazy val isRetryEnabled: Boolean        = true
    override lazy val isBulkRetryEnabled: Boolean    = false
    override def generateNewUUID: String             = uuid
    def loggerForTest: play.api.Logger               = logger
  }

  def createJsonPayload(individualDetails: IndividualDetails): JsValue = Json.parse(
    s"""
       |{
       |  "nino": "${individualDetails.nino}",
       |  "firstName": "${individualDetails.firstName}",
       |  "lastName": "${individualDetails.lastName}",
       |  "dob": "${individualDetails.dateOfBirth.format(formatter)}",
       |  "pensionSchemeOrganisationID": "$userId"
       |}
       |""".stripMargin
  )

  def verifyDesPost(
    member: IndividualDetails,
    expectedCount: Int = 1
  ): Unit =
    server.verify(
      expectedCount,
      postRequestedFor(urlEqualTo("/individuals/residency-status/"))
        .withHeader("Environment", equalTo("DES HEADER"))
        .withHeader("OriginatorId", equalTo("DA_RAS"))
        .withHeader("Authorization", equalTo("Bearer DES AUTH TOKEN"))
        .withHeader("Content-Type", containing("application/json"))
        .withHeader("CorrelationId", equalTo(uuid))
        .withRequestBody(equalToJson(createJsonPayload(member).toString()))
    )

  def stubDesResponse(status: Int, body: String = ""): Unit =
    server.stubFor(
      post(urlEqualTo("/individuals/residency-status/"))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(body)
        )
    )

  "DESConnector getResidencyStatus with matching" should {
    "handle successful response when 200 is returned from des and CY and CYPlusOne is present" in {
      val successResponse = ResidencyStatusSuccess(
        nino = "AB123456C",
        deathDate = Some(""),
        deathDateStatus = Some(""),
        deseasedIndicator = Some(false),
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = Some("Scottish")
      )

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))
      stubDesResponse(200, Json.toJson(successResponse).toString())

      val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId))
      result.isLeft                       shouldBe true
      result.left.getOrElse("Get Failed") shouldBe ResidencyStatus("otherUKResident", Some("scotResident"))
      verifyDesPost(individualDetails)
    }

    "handle retryable 429 responses and then succeed" in {
      given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats
      val errorResponse                              = ResidencyStatusFailure("TOO_MANY_REQUESTS", "Too Many Requests.")
      val successResponse                            = ResidencyStatusSuccess(
        nino = "AB123456C",
        deathDate = Some(""),
        deathDateStatus = Some(""),
        deseasedIndicator = Some(false),
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = Some("Scottish")
      )

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))
      val scenarioName      = "retry-429-success"
      val desUrl            = "/individuals/residency-status/"

      server.stubFor(
        post(urlEqualTo(desUrl))
          .inScenario(scenarioName)
          .whenScenarioStateIs(STARTED)
          .willReturn(
            aResponse()
              .withStatus(429)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(errorResponse).toString())
          )
          .willSetStateTo("Second Request")
      )

      server.stubFor(
        post(urlEqualTo(desUrl))
          .inScenario(scenarioName)
          .whenScenarioStateIs("Second Request")
          .willReturn(
            aResponse()
              .withStatus(200)
              .withHeader("Content-Type", "application/json")
              .withBody(Json.toJson(successResponse).toString())
          )
      )

      val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId, isBulkRequest = true))
      result.isRight                      shouldBe false
      result.left.getOrElse("Get Failed") shouldBe ResidencyStatus("otherUKResident", Some("scotResident"))
      verifyDesPost(individualDetails, expectedCount = 2)
    }
  }

  "DESConnector correlationID" when {
    "requestID is present in the headerCarrier" should {
      "return new ID pre-appending the requestID when the requestID matches the format(8-4-4-4)" in {
        val requestId = "abcd0000-dh12-fg34-ij56"
        TestDesConnector.correlationId(using
          HeaderCarrier(requestId = Some(RequestId(requestId)))
        ) shouldBe s"$requestId-${uuid.substring(24)}"
      }

      "return new ID when the requestID does not match the format(8-4-4-4)" in {
        val requestId = "1a2b-dh12-fg34-ij56"
        TestDesConnector.correlationId(using HeaderCarrier(requestId = Some(RequestId(requestId)))) shouldBe uuid
      }
    }

    "requestID is not present in the headerCarrier should return a new ID" in {
      TestDesConnector.correlationId(using HeaderCarrier()) shouldBe uuid
    }
  }

  "DESConnector getResidencyStatus correlationId propagation" should {
    "derive the CorrelationId header sent to DES from the request ID in the supplied HeaderCarrier" in {
      val requestId                      = "abcd0000-dh12-fg34-ij56"
      val hcWithRequestId: HeaderCarrier = HeaderCarrier(requestId = Some(RequestId(requestId)))
      val successResponse                = ResidencyStatusSuccess(
        nino = "AB123456C",
        deathDate = Some(""),
        deathDateStatus = Some(""),
        deseasedIndicator = Some(false),
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = Some("Scottish")
      )

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))
      stubDesResponse(200, Json.toJson(successResponse).toString())

      await(TestDesConnector.getResidencyStatus(individualDetails, userId)(using hcWithRequestId))

      server.verify(
        1,
        postRequestedFor(urlEqualTo("/individuals/residency-status/"))
          .withHeader("CorrelationId", equalTo(s"$requestId-${uuid.substring(24)}"))
      )
    }
  }

  "DESConnector logging" should {
    "log the correlation id at ERROR level when a DES call fails" in {
      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))
      val requestId         = "abcd6789-dh12-fg34-ij56"
      val expectedCorrId    = s"$requestId-${uuid.substring(24)}"

      server.stubFor(
        post(urlEqualTo("/individuals/residency-status/"))
          .willReturn(
            aResponse().withStatus(403).withBody("""{"code":"STATUS_UNAVAILABLE"}""")
          )
      )

      withCaptureOfLoggingFrom(TestDesConnector.loggerForTest) { logs =>
        await(
          TestDesConnector.getResidencyStatus(individualDetails, userId)(using
            HeaderCarrier(requestId = Some(RequestId(requestId)))
          )
        )
        logs.map(event => event.getLevel -> event.getMessage) should contain(
          (
            Level.ERROR,
            s"[DesConnector][getResidencyStatus] Cannot provide a residency status for this pension scheme member for userId ($userId) correlationId ($expectedCorrId)."
          )
        )
      }
    }
  }

}
