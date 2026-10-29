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

import com.github.tomakehurst.wiremock.client.WireMock.{
  aResponse, containing, equalTo, equalToJson, post, postRequestedFor, urlEqualTo
}
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsValue, Json, OFormat}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.*
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClientV2Provider
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.models.*
import uk.gov.hmrc.rasapi.services.AuditService
import uk.gov.hmrc.rasapi.utils.WireMockHelper

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.ExecutionContext

class DesConnectorSpec
    extends AnyWordSpecLike with Matchers with GuiceOneAppPerSuite with MockitoSugar with WireMockHelper {

  given hc: HeaderCarrier = HeaderCarrier()

  val mockAuditService: AuditService            = mock[AuditService]
  given format: OFormat[ResidencyStatusSuccess] = ResidencyStatusFormats.successFormats

  val servicesConfig: ServicesConfig = app.injector.instanceOf[ServicesConfig]
  val appContext: AppContext         = app.injector.instanceOf[AppContext]

  lazy val realHttp: HttpClientV2Provider =
    app.injector.instanceOf[HttpClientV2Provider]

  val uuid = "123e4567-e89b-42d3-a456-556642440000"

  lazy val wireMockDesConnector: DesConnector =
    createDesConnector()

  def createDesConnector(
    allowNoNextYearStatusEnabled: Boolean = true,
    retryEnabled: Boolean = true,
    bulkRetryEnabled: Boolean = false
  ): DesConnector =
    new DesConnector(realHttp, mockAuditService, appContext)(using ExecutionContext.global) {

      override lazy val desBaseUrl: String =
        s"http://localhost:${server.port()}"

      override lazy val allowNoNextYearStatus: Boolean =
        allowNoNextYearStatusEnabled

      override lazy val retryLimit: Int = 3
      override lazy val retryDelay: Int = 500

      override lazy val desUrlHeaderEnv: String = "DES HEADER"
      override lazy val desAuthToken: String    = "DES AUTH TOKEN"

      override lazy val isRetryEnabled: Boolean     = retryEnabled
      override lazy val isBulkRetryEnabled: Boolean = bulkRetryEnabled

      override def generateNewUUID: String = uuid
    }

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  val userId = "A123456"

  def createJsonPayload(individualDetails: IndividualDetails): JsValue = Json.parse(
    s"""
       |{
       |  "nino": "${individualDetails.nino}",
       |  "firstName": "${individualDetails.firstName}",
       |  "lastName": "${individualDetails.lastName}",
       |  "dob": "${individualDetails.dateOfBirth.format(formatter)}",
       |  "pensionSchemeOrganisationID": "$userId"
       |}
    """.stripMargin
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
        .withRequestBody(
          equalToJson(createJsonPayload(member).toString())
        )
    )

  def stubDesResponse(
    status: Int,
    body: String = ""
  ): Unit =
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

      val successresponse = ResidencyStatusSuccess(
        nino = "AB123456C",
        deathDate = Some(""),
        deathDateStatus = Some(""),
        deseasedIndicator = Some(false),
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = Some("Scottish")
      )

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

      stubDesResponse(
        200,
        Json.toJson(successresponse).toString()
      )

      val result =
        await(wireMockDesConnector.getResidencyStatus(individualDetails, userId))
      result.isLeft                       shouldBe true
      result.left.getOrElse("Get Failed") shouldBe ResidencyStatus("otherUKResident", Some("scotResident"))

      verifyDesPost(individualDetails)
    }

    "When the requested API version is V2.0" should {

      "handle successful response when 200 is returned from des where CY and CYPlusOne is present for a Welsh resident" in {

        val successResponse = ResidencyStatusSuccess(
          nino = "AA246255",
          deathDate = Some(""),
          deathDateStatus = Some(""),
          deseasedIndicator = Some(false),
          currentYearResidencyStatus = "Welsh",
          nextYearResidencyStatus = Some("Welsh")
        )

        val individualDetails =
          IndividualDetails("AB123456C", "JACK", "OSCAR", LocalDate.parse("1962-07-07", formatter))

        stubDesResponse(200, Json.toJson(successResponse).toString())

        val result = await(wireMockDesConnector.getResidencyStatus(individualDetails, userId))
        result shouldBe Left(ResidencyStatus("welshResident", Some("welshResident")))

        verifyDesPost(individualDetails)
      }

      "handle successful response when 200 is returned from des where CY and CYPlusOne is present for a Scottish resident moving in Wales" in {

        val successResponse = ResidencyStatusSuccess(
          nino = "SG123480",
          deathDate = Some(""),
          deathDateStatus = Some(""),
          deseasedIndicator = Some(false),
          currentYearResidencyStatus = "Welsh",
          nextYearResidencyStatus = Some("Scottish")
        )

        val individualDetails =
          IndividualDetails("SG123480", "HELEN", "SMITH", LocalDate.parse("1975-02-18", formatter))

        stubDesResponse(200, Json.toJson(successResponse).toString())

        val result = await(wireMockDesConnector.getResidencyStatus(individualDetails, userId))
        result shouldBe Left(ResidencyStatus("welshResident", Some("scotResident")))

        verifyDesPost(individualDetails)
      }

    }

    "handle successful response when 200 is returned from des and only CY is present and feature toggle is turned on" in {

      val successResponse = ResidencyStatusSuccess(
        nino = "AB123456C",
        deathDate = Some(""),
        deathDateStatus = Some(""),
        deseasedIndicator = None,
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = None
      )

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

      stubDesResponse(200, Json.toJson(successResponse).toString())

      val result = await(wireMockDesConnector.getResidencyStatus(individualDetails, userId))
      result.isLeft                       shouldBe true
      result.left.getOrElse("Get Failed") shouldBe ResidencyStatus("otherUKResident", None)

      verifyDesPost(individualDetails)
    }

    "handle unsuccessful response for bulk request when 429 is returned from des the first time around and CY and " +
      "CYPlusOne is present and bulk retry is true but retry enabled is false" in {

        given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats

        val connector = createDesConnector(
          retryEnabled = false,
          bulkRetryEnabled = true
        )

        val errorResponse   = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")
        val successResponse = ResidencyStatusSuccess(
          nino = "AB123456C",
          deathDate = Some(""),
          deathDateStatus = Some(""),
          deseasedIndicator = Some(false),
          currentYearResidencyStatus = "Uk",
          nextYearResidencyStatus = Some("Scottish")
        )

        val individualDetails =
          IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

        val desUrl       = "/individuals/residency-status/"
        val scenarioName = "bulk-retry-429-then-success"

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

        val result = await(
          connector.getResidencyStatus(
            individualDetails,
            userId,
            isBulkRequest = true
          )
        )

        result.isRight                      shouldBe false
        result.left.getOrElse("Get Failed") shouldBe ResidencyStatus("otherUKResident", Some("scotResident"))

        verifyDesPost(individualDetails, expectedCount = 2)
      }

    "handle unsuccessful response for bulk request when 429 is returned from des the first time around and CY and " +
      "CYPlusOne is present and bulk retry is false and retry enabled is false" in {

        given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats

        val connector = createDesConnector(
          retryEnabled = false,
          bulkRetryEnabled = false
        )

        val errorResponse = ResidencyStatusFailure("TOO_MANY_REQUESTS", "Too Many Requests.")

        val individualDetails =
          IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))
        stubDesResponse(429, Json.toJson(errorResponse).toString())

        val result = await(
          connector.getResidencyStatus(
            individualDetails,
            userId,
            isBulkRequest = true
          )
        )

        result.isLeft                  shouldBe false
        result.getOrElse("Get Failed") shouldBe errorResponse

        verifyDesPost(individualDetails)
      }

    "handle successful response when 200 is returned from des and only CY is present and no next year status toggle is turned off" in {
      val connector         = createDesConnector(
        allowNoNextYearStatusEnabled = false
      )
      val errorResponse     =
        ResidencyStatusFailure(connector.error_InternalServerError, "Internal server error.")
      val successResponse   = ResidencyStatusSuccess(
        nino = "AB123456C",
        deathDate = Some(""),
        deathDateStatus = Some(""),
        deseasedIndicator = Some(false),
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = None
      )
      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

      stubDesResponse(200, Json.toJson(successResponse).toString())
      val result = await(
        connector.getResidencyStatus(individualDetails, userId)
      )
      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe errorResponse
      verifyDesPost(individualDetails)
    }

    "handle failure response (no match) from des" in {
      given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats
      val errorResponse                              = ResidencyStatusFailure(
        "STATUS_UNAVAILABLE",
        "Cannot provide a residency status for this pension scheme member."
      )

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      stubDesResponse(404, Json.toJson(errorResponse).toString())
      val result = await(wireMockDesConnector.getResidencyStatus(individualDetails, userId))

      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe errorResponse

      verifyDesPost(individualDetails)
    }
    "handle forbidden response from des" in {

      val errorResponse = ResidencyStatusFailure(
        appContext.matchingFailedStatus,
        "Cannot provide a residency status for this pension scheme member."
      )

      val individualDetails =
        IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      stubDesResponse(403, """{"code":"STATUS_UNAVAILABLE"}""")

      val result =
        await(wireMockDesConnector.getResidencyStatus(individualDetails, userId))
      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe errorResponse
      verifyDesPost(individualDetails)
    }
    "handle success response but the person is deceased from des" in {
      val successResponse = ResidencyStatusSuccess(
        nino = "AB123456C",
        deathDate = Some("2017-12-25"),
        deathDateStatus = None,
        deseasedIndicator = Some(true),
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = Some("Uk")
      )

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      stubDesResponse(200, Json.toJson(successResponse).toString())

      val expectedResult =
        ResidencyStatusFailure("DECEASED", "Cannot provide a residency status for this pension scheme member.")

      val result = await(wireMockDesConnector.getResidencyStatus(individualDetails, userId))

      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe expectedResult

      verifyDesPost(individualDetails)
    }

    "handle unexpected responses as 500 from des" in {
      given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats
      val errorResponse                              = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      stubDesResponse(500, Json.toJson(errorResponse).toString())

      val result =
        await(wireMockDesConnector.getResidencyStatus(individualDetails, userId))

      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe errorResponse

      verifyDesPost(individualDetails, expectedCount = 3)
    }

    "handle Service Unavailable (503) response from des" in {
      given formatF: OFormat[ResidencyStatusFailure] =
        ResidencyStatusFormats.failureFormats
      val errorResponse                              = ResidencyStatusFailure("SERVICE_UNAVAILABLE", "Service unavailable")

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      stubDesResponse(503)

      val result = await(wireMockDesConnector.getResidencyStatus(individualDetails, userId))

      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe errorResponse

      verifyDesPost(individualDetails, expectedCount = 3)
    }

    "handle bad request from des" in {
      given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats
      val expectedErrorResponse                      = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")
      val errorResponse                              = ResidencyStatusFailure("DO_NOT_RE_PROCESS", "Internal server error.")

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      stubDesResponse(400, Json.toJson(errorResponse).toString())

      val result =
        await(wireMockDesConnector.getResidencyStatus(individualDetails, userId))

      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe expectedErrorResponse

      verifyDesPost(individualDetails)
    }
    "handle invalid JSON response from des" in {

      val individualDetails =
        IndividualDetails(
          "AB123456C",
          "JOHN",
          "SMITH",
          LocalDate.parse("1990-02-21", formatter)
        )

      stubDesResponse(200, "invalid json")

      val result = await(
        wireMockDesConnector.getResidencyStatus(individualDetails, userId)
      )

      result shouldBe Right(
        ResidencyStatusFailure(
          "INTERNAL_SERVER_ERROR",
          "Internal server error."
        )
      )

      verifyDesPost(individualDetails, expectedCount = 3)
    }

    "Handle requests" when {
      "it cannot be processed the first time round" in {

        given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats
        val errorResponse                              = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")
        val successResponse                            = ResidencyStatusSuccess(
          nino = "AB123456C",
          deathDate = Some(""),
          deathDateStatus = Some(""),
          deseasedIndicator = Some(false),
          currentYearResidencyStatus = "Uk",
          nextYearResidencyStatus = None
        )

        val individualDetails =
          IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

        val desUrl       = "/individuals/residency-status/"
        val scenarioName = "retry-429-then-success"

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

        val result =
          await(wireMockDesConnector.getResidencyStatus(individualDetails, userId))

        result.left.getOrElse("Get Failed") shouldBe ResidencyStatus("otherUKResident")
        result.isRight                      shouldBe false

        verifyDesPost(individualDetails, expectedCount = 2)
      }

      "429 (Too Many Requests) has been returned 3 times" in {

        given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats

        val errorResponse = ResidencyStatusFailure("TOO_MANY_REQUESTS", "Too Many Requests.")

        val individualDetails =
          IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

        stubDesResponse(429)

        val result =
          await(wireMockDesConnector.getResidencyStatus(individualDetails, userId))
        result.isLeft                  shouldBe false
        result.getOrElse("Get Failed") shouldBe errorResponse

        verifyDesPost(individualDetails, expectedCount = 3)
      }
    }
  }

  "DESConnector correlationID" when {
    "requestID is present in the headerCarrier" should {
      "return new ID pre-appending the requestID when the requestID matches the format(8-4-4-4)" in {
        val requestId = "abcd0000-dh12-fg34-ij56"
        wireMockDesConnector.correlationId(using
          HeaderCarrier(requestId = Some(RequestId(requestId)))
        ) shouldBe s"$requestId-${uuid.substring(24)}"
      }

      "return new ID when the requestID does not match the format(8-4-4-4)" in {
        val requestId = "1a2b-dh12-fg34-ij56"
        wireMockDesConnector.correlationId(using HeaderCarrier(requestId = Some(RequestId(requestId)))) shouldBe uuid
      }
    }

    "requestID is not present in the headerCarrier should return a new ID" in {
      wireMockDesConnector.correlationId(using HeaderCarrier()) shouldBe uuid
    }
  }

}
