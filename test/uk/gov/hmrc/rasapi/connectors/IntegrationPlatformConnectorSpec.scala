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

import com.github.tomakehurst.wiremock.client.WireMock.*
import com.github.tomakehurst.wiremock.http.Fault
import com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
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

trait IntegrationPlatformConnectorSpec
    extends AnyWordSpecLike
    with Matchers
    with GuiceOneAppPerSuite
    with MockitoSugar
    with WireMockHelper
    with Eventually {

  override def fakeApplication(): Application =
    new GuiceApplicationBuilder()
      .configure(
        "play.ws.timeout.request" -> "1s",
        "play.ws.timeout.idle"    -> "1s"
      )
      .build()

  def isHip5161Enabled: Boolean

  def integrationPlatformPath: String

  given hc: HeaderCarrier = HeaderCarrier()

  val mockAuditService: AuditService            = mock[AuditService]
  given format: OFormat[ResidencyStatusSuccess] = ResidencyStatusFormats.successFormats

  val servicesConfig: ServicesConfig = app.injector.instanceOf[ServicesConfig]
  val appContext: AppContext         = app.injector.instanceOf[AppContext]

  lazy val integrationPlatform: String = if (isHip5161Enabled) "HIP" else "DES"

  lazy val realHttp: HttpClientV2Provider = app.injector.instanceOf[HttpClientV2Provider]

  val uuid = "123e4567-e89b-42d3-a456-556642440000"

  lazy val integrationPlatformConnector: IntegrationPlatformConnector = createIntegrationPlatformConnector()

  def createIntegrationPlatformConnector(
    allowNoNextYearStatusEnabled: Boolean = true,
    retryEnabled: Boolean = true,
    bulkRetryEnabled: Boolean = false
  ): IntegrationPlatformConnector

  val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  val individualDetails: IndividualDetails =
    IndividualDetails("LE241131B", "Joe", "Bloggs", LocalDate.parse("1990-12-03", formatter))

  val userId = "A123456"

  val residencyStatus: ResidencyStatus =
    ResidencyStatus(currentYearResidencyStatus = "scotResident", nextYearForecastResidencyStatus = Some("scotResident"))

  val residencyStatusFailure: ResidencyStatusFailure = ResidencyStatusFailure("500", "HODS NOT AVAILABLE")

  val residencyStatusJson: JsValue = Json.parse(
    """{
         "currentYearResidencyStatus" : "scotResident",
         "nextYearForecastResidencyStatus" : "scotResident"
        }
    """.stripMargin
  )

  def createJsonPayload(individualDetails: IndividualDetails): JsValue
  def verifyIntegrationPlatformPost(member: IndividualDetails, expectedCount: Int = 1): Unit
  def stubIntegrationPlatformResponse(status: Int, body: String = ""): Unit

  def makeSuccessResponse(
    nino: String,
    deathDate: Option[String],
    deathDateStatus: Option[String],
    deceasedIndicator: Option[Boolean],
    currentYearResidencyStatus: String,
    nextYearResidencyStatus: Option[String]
  ): ResidencyStatusSuccess = ResidencyStatusSuccess(
    nino,
    deathDate,
    deathDateStatus,
    deceasedIndicator,
    currentYearResidencyStatus,
    nextYearResidencyStatus
  )

  s"$integrationPlatform Connector getResidencyStatus with matching" should {

    "handle successful response when 200 is returned from integrationPlatformRouter and CY and CYPlusOne is present" in {

      val successResponse = makeSuccessResponse(
        nino = "AB123456C",
        deathDate = Some(""),
        deathDateStatus = Some(""),
        deceasedIndicator = Some(false),
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = Some("Scottish")
      )

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

      stubIntegrationPlatformResponse(200, Json.toJson(successResponse).toString())

      val result = await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId))
      result.isLeft                       shouldBe true
      result.left.getOrElse("Get Failed") shouldBe ResidencyStatus("otherUKResident", Some("scotResident"))

      verifyIntegrationPlatformPost(individualDetails)
    }

    "When the requested API version is V2.0" should {

      "handle successful response when 200 is returned from integrationPlatformRouter where CY and CYPlusOne is present for a Welsh resident" in {

        val successResponse = makeSuccessResponse(
          nino = "AA246255",
          deathDate = Some(""),
          deathDateStatus = Some(""),
          deceasedIndicator = Some(false),
          currentYearResidencyStatus = "Welsh",
          nextYearResidencyStatus = Some("Welsh")
        )

        val individualDetails =
          IndividualDetails("AB123456C", "JACK", "OSCAR", LocalDate.parse("1962-07-07", formatter))

        stubIntegrationPlatformResponse(200, Json.toJson(successResponse).toString())

        val result = await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId))
        result shouldBe Left(ResidencyStatus("welshResident", Some("welshResident")))

        verifyIntegrationPlatformPost(individualDetails)
      }

      "handle successful response when 200 is returned from integrationPlatformRouter where CY and CYPlusOne is present for a Scottish resident moving in Wales" in {

        val successResponse = makeSuccessResponse(
          nino = "SG123480",
          deathDate = Some(""),
          deathDateStatus = Some(""),
          deceasedIndicator = Some(false),
          currentYearResidencyStatus = "Welsh",
          nextYearResidencyStatus = Some("Scottish")
        )

        val individualDetails =
          IndividualDetails("SG123480", "HELEN", "SMITH", LocalDate.parse("1975-02-18", formatter))

        stubIntegrationPlatformResponse(200, Json.toJson(successResponse).toString())

        val result = await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId))
        result shouldBe Left(ResidencyStatus("welshResident", Some("scotResident")))

        verifyIntegrationPlatformPost(individualDetails)
      }

    }

    "handle successful response when 200 is returned from integrationPlatformRouter and only CY is present and feature toggle is turned on" in {

      val successResponse = makeSuccessResponse(
        nino = "AB123456C",
        deathDate = Some(""),
        deathDateStatus = Some(""),
        deceasedIndicator = None,
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = None
      )

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

      stubIntegrationPlatformResponse(200, Json.toJson(successResponse).toString())

      val result = await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId))
      result.isLeft                       shouldBe true
      result.left.getOrElse("Get Failed") shouldBe ResidencyStatus("otherUKResident", None)

      verifyIntegrationPlatformPost(individualDetails)
    }

    "handle unsuccessful response for bulk request when 429 is returned from integrationPlatformRouter the first time around and CY and " +
      "CYPlusOne is present and bulk retry is true but retry enabled is false" in {

        given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats

        val errorResponse   = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")
        val successResponse = makeSuccessResponse(
          nino = "AB123456C",
          deathDate = Some(""),
          deathDateStatus = Some(""),
          deceasedIndicator = Some(false),
          currentYearResidencyStatus = "Uk",
          nextYearResidencyStatus = Some("Scottish")
        )

        val individualDetails =
          IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

        val scenarioName = "bulk-retry-429-then-success"

        server.stubFor(
          post(urlEqualTo(integrationPlatformPath))
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
          post(urlEqualTo(integrationPlatformPath))
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
          await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId, isBulkRequest = true))

        result.isRight                      shouldBe false
        result.left.getOrElse("Get Failed") shouldBe ResidencyStatus("otherUKResident", Some("scotResident"))

        verifyIntegrationPlatformPost(individualDetails, expectedCount = 2)
      }

    "handle unsuccessful response for bulk request when 429 is returned from integrationPlatformRouter the first time around and CY and " +
      "CYPlusOne is present and bulk retry is false and retry enabled is false" in {

        given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats

        val errorResponse = ResidencyStatusFailure("TOO_MANY_REQUESTS", "Too Many Requests.")

        val individualDetails =
          IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))
        stubIntegrationPlatformResponse(429, Json.toJson(errorResponse).toString())

        val connector = createIntegrationPlatformConnector(retryEnabled = false)

        val result = await(connector.getResidencyStatus(individualDetails, userId, isBulkRequest = true))
        result.isLeft                  shouldBe false
        result.getOrElse("Get Failed") shouldBe errorResponse

        verifyIntegrationPlatformPost(individualDetails)
      }

    "handle successful response when 200 is returned from integrationPlatformRouter and only CY is present and no next year status toggle is turned off" in {

      given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats

      val errorResponse =
        ResidencyStatusFailure(integrationPlatformConnector.error_InternalServerError, "Internal server error.")

      val successResponse = makeSuccessResponse(
        nino = "AB123456C",
        deathDate = Some(""),
        deathDateStatus = Some(""),
        deceasedIndicator = Some(false),
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = None
      )

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

      stubIntegrationPlatformResponse(200, Json.toJson(successResponse).toString())

      val connector = createIntegrationPlatformConnector(allowNoNextYearStatusEnabled = false)
      val result    = await(connector.getResidencyStatus(individualDetails, userId))
      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe errorResponse
      verifyIntegrationPlatformPost(individualDetails)
    }

    "handle failure response (no match) from integrationPlatformRouter" in {
      given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats
      val errorResponse                              = ResidencyStatusFailure(
        "STATUS_UNAVAILABLE",
        "Cannot provide a residency status for this pension scheme member."
      )

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      stubIntegrationPlatformResponse(404, Json.toJson(errorResponse).toString())
      val result = await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId))

      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe errorResponse

      verifyIntegrationPlatformPost(individualDetails)
    }
    "handle forbidden response from integrationPlatformRouter" in {

      val errorResponse = ResidencyStatusFailure(
        appContext.matchingFailedStatus,
        "Cannot provide a residency status for this pension scheme member."
      )

      val individualDetails =
        IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      stubIntegrationPlatformResponse(403, """{"code":"STATUS_UNAVAILABLE"}""")

      val result = await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId))
      println(result)
      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe errorResponse
      verifyIntegrationPlatformPost(individualDetails)
    }
    "handle success response but the person is deceased from integrationPlatformRouter" in {
      val successResponse = makeSuccessResponse(
        nino = "AB123456C",
        deathDate = Some("2017-12-25"),
        deathDateStatus = None,
        deceasedIndicator = Some(true),
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = Some("Uk")
      )

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      stubIntegrationPlatformResponse(200, Json.toJson(successResponse).toString())

      val expectedResult =
        ResidencyStatusFailure("DECEASED", "Cannot provide a residency status for this pension scheme member.")

      val result = await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId))

      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe expectedResult

      verifyIntegrationPlatformPost(individualDetails)
    }

    "handle unexpected responses as 500 from integrationPlatformRouter" in {
      given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats

      val errorResponse = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      stubIntegrationPlatformResponse(500, Json.toJson(errorResponse).toString())

      val result = await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId))

      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe errorResponse

      verifyIntegrationPlatformPost(individualDetails, expectedCount = 3)
    }

    "handle Service Unavailable (503) response from integrationPlatformRouter" in {
      val errorResponse = ResidencyStatusFailure("SERVICE_UNAVAILABLE", "Service unavailable")

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      stubIntegrationPlatformResponse(503)

      val result = await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId))

      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe errorResponse

      verifyIntegrationPlatformPost(individualDetails, expectedCount = 3)
    }

    "handle bad request from integrationPlatformRouter" in {
      given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats

      val expectedErrorResponse = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")
      val errorResponse         = ResidencyStatusFailure("DO_NOT_RE_PROCESS", "Internal server error.")

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      stubIntegrationPlatformResponse(400, Json.toJson(errorResponse).toString())

      val result = await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId))

      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe expectedErrorResponse

      verifyIntegrationPlatformPost(individualDetails)
    }

    s"handle GatewayTimeoutException when $integrationPlatform request times out" in {

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      server.stubFor(
        post(urlEqualTo(integrationPlatformPath))
          .willReturn(
            aResponse()
              .withStatus(200)
              .withFixedDelay(4000)
          )
      )

      val result = await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId))

      result shouldBe Right(ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error."))

      eventually(
        timeout(Span(8, Seconds)),
        interval(Span(200, Millis))
      ) {
        verifyIntegrationPlatformPost(individualDetails)
      }
    }

    s"handle unexpected connection failure from $integrationPlatform" in {

      val connector = createIntegrationPlatformConnector(retryEnabled = false)

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

      server.stubFor(
        post(urlEqualTo(integrationPlatformPath))
          .willReturn(
            aResponse()
              .withFault(Fault.CONNECTION_RESET_BY_PEER)
          )
      )

      val result = await(connector.getResidencyStatus(individualDetails, userId))

      result shouldBe Right(ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error."))
      verifyIntegrationPlatformPost(individualDetails)
    }

    s"handle invalid JSON response from $integrationPlatform" in {

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

      stubIntegrationPlatformResponse(200, "invalid json")

      val result = await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId))

      result shouldBe Right(ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error."))
      verifyIntegrationPlatformPost(individualDetails, expectedCount = 3)
    }

    "Handle requests" when {
      "it cannot be processed the first time round" in {

        given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats
        val errorResponse                              = ResidencyStatusFailure("TOO_MANY_REQUESTS", "Too Many Requests.")
        val successResponse                            = makeSuccessResponse(
          nino = "AB123456C",
          deathDate = Some(""),
          deathDateStatus = Some(""),
          deceasedIndicator = Some(false),
          currentYearResidencyStatus = "Uk",
          nextYearResidencyStatus = None
        )

        val individualDetails =
          IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

        val scenarioName = "retry-429-then-success"

        server.stubFor(
          post(urlEqualTo(integrationPlatformPath))
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
          post(urlEqualTo(integrationPlatformPath))
            .inScenario(scenarioName)
            .whenScenarioStateIs("Second Request")
            .willReturn(
              aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody(Json.toJson(successResponse).toString())
            )
        )

        val result = await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId))

        result.left.getOrElse("Get Failed") shouldBe ResidencyStatus("otherUKResident")
        result.isRight                      shouldBe false

        verifyIntegrationPlatformPost(individualDetails, expectedCount = 2)
      }

      "429 (Too Many Requests) has been returned 3 times" in {

        val errorResponse = ResidencyStatusFailure("TOO_MANY_REQUESTS", "Too Many Requests.")

        val individualDetails =
          IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

        stubIntegrationPlatformResponse(429)

        val result = await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId))
        result.isLeft                  shouldBe false
        result.getOrElse("Get Failed") shouldBe errorResponse

        verifyIntegrationPlatformPost(individualDetails, expectedCount = 3)
      }
    }
  }

}

class DesConnectorSpec extends IntegrationPlatformConnectorSpec {
  def isHip5161Enabled: Boolean = false

  val integrationPlatformPath: String = "/individuals/residency-status/"

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

  def createIntegrationPlatformConnector(
    allowNoNextYearStatusEnabled: Boolean,
    retryEnabled: Boolean,
    bulkRetryEnabled: Boolean
  ): DesConnector =
    new DesConnector(realHttp, mockAuditService, appContext)(using ExecutionContext.global) {
      override lazy val baseUrl = s"http://localhost:${server.port()}"

      override lazy val allowNoNextYearStatus: Boolean = allowNoNextYearStatusEnabled

      override lazy val retryLimit: Int = 3
      override lazy val retryDelay: Int = 500

      override lazy val desUrlHeaderEnv: String = "DES HEADER"
      override lazy val desAuthToken: String    = "DES AUTH TOKEN"

      override lazy val isRetryEnabled: Boolean     = retryEnabled
      override lazy val isBulkRetryEnabled: Boolean = bulkRetryEnabled

      override def generateNewUUID: String = uuid
    }

  def verifyIntegrationPlatformPost(
    member: IndividualDetails,
    expectedCount: Int = 1
  ): Unit =
    server.verify(
      expectedCount,
      postRequestedFor(urlEqualTo(integrationPlatformPath))
        .withHeader("Environment", equalTo("DES HEADER"))
        .withHeader("OriginatorId", equalTo("DA_RAS"))
        .withHeader("Authorization", equalTo("Bearer DES AUTH TOKEN"))
        .withHeader("Content-Type", containing("application/json"))
        .withHeader("CorrelationId", equalTo(uuid))
        .withRequestBody(
          equalToJson(createJsonPayload(member).toString())
        )
    )

  def stubIntegrationPlatformResponse(
    status: Int,
    body: String = ""
  ): Unit =
    server.stubFor(
      post(urlEqualTo(integrationPlatformPath))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(body)
        )
    )

  val testDesConnector: DesConnector = createIntegrationPlatformConnector()

  "DES Connector integration" when {

    "having an http response with mismatched status or future" should {

      s"handle 'valid' failure JSON returned with HTTP 200 from DES" in {
        given formatF: OFormat[ResidencyStatusFailure] =
          ResidencyStatusFormats.failureFormats

        val individualDetails =
          IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

        val failureResponse =
          ResidencyStatusFailure(
            "STATUS_UNAVAILABLE",
            "Cannot provide a residency status for this pension scheme member."
          )

        stubIntegrationPlatformResponse(200, Json.toJson(failureResponse).toString())

        val result = await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId))

        result shouldBe Right(failureResponse)
        verifyIntegrationPlatformPost(individualDetails)
      }
    }

    "Producing correlationID and the requestID is present in the headerCarrier" should {

      "return new ID pre-appending the requestID when the requestID matches the format(8-4-4-4)" in {
        val requestId = "abcd0000-dh12-fg34-ij56"
        testDesConnector.correlationId(using
          HeaderCarrier(requestId = Some(RequestId(requestId)))
        ) shouldBe s"$requestId-${uuid.substring(24)}"
      }

      "return new ID when the requestID does not match the format(8-4-4-4)" in {
        val requestId = "1a2b-dh12-fg34-ij56"
        testDesConnector.correlationId(using HeaderCarrier(requestId = Some(RequestId(requestId)))) shouldBe uuid
      }

      "requestID is not present in the headerCarrier should return a new ID" in {
        testDesConnector.correlationId(using HeaderCarrier()) shouldBe uuid
      }
    }
  }

}

class HipConnectorSpec extends IntegrationPlatformConnectorSpec {
  def isHip5161Enabled: Boolean = true

  val integrationPlatformPath: String = "/paye/person/regional-residency-status"

  def createJsonPayload(individualDetails: IndividualDetails): JsValue = Json.parse(
    s"""
       |{
       |  "identifier": "${individualDetails.nino}",
       |  "firstForename": "${individualDetails.firstName}",
       |  "surname": "${individualDetails.lastName}",
       |  "dateOfBirth": "${individualDetails.dateOfBirth.format(formatter)}"
       |}
      """.stripMargin
  )

  override def makeSuccessResponse(
    nino: String,
    deathDate: Option[String],
    deathDateStatus: Option[String],
    deceasedIndicator: Option[Boolean],
    currentYearResidencyStatus: String,
    nextYearResidencyStatus: Option[String]
  ): ResidencyStatusSuccess =
    super
      .makeSuccessResponse(
        nino,
        deathDate,
        deathDateStatus,
        deceasedIndicator,
        currentYearResidencyStatus,
        nextYearResidencyStatus
      )
      .copy(statusCheckTimeStamp = Some("2027-08-24T14:15:22Z"))

  def createIntegrationPlatformConnector(
    allowNoNextYearStatusEnabled: Boolean,
    retryEnabled: Boolean,
    bulkRetryEnabled: Boolean
  ): HipConnector =
    new HipConnector(realHttp, mockAuditService, appContext)(using ExecutionContext.global) {
      override lazy val baseUrl = s"http://localhost:${server.port()}"

      override lazy val allowNoNextYearStatus: Boolean = allowNoNextYearStatusEnabled

      override lazy val retryLimit: Int = 3
      override lazy val retryDelay: Int = 500

      override lazy val isRetryEnabled: Boolean     = retryEnabled
      override lazy val isBulkRetryEnabled: Boolean = bulkRetryEnabled

      override def generateNewUUID: String = uuid
    }

  lazy val hipOriginator: String = appContext.servicesConfig.getString("microservice.services.hip.originatorId")

  def verifyIntegrationPlatformPost(
    member: IndividualDetails,
    expectedCount: Int = 1
  ): Unit =
    server.verify(
      expectedCount,
      postRequestedFor(urlEqualTo(integrationPlatformPath))
        .withHeader("Authorization", matching("Basic .+"))
        .withHeader("Content-Type", containing("application/json"))
        .withHeader("correlationId", equalTo(uuid))
        .withHeader("gov-uk-originator-id", equalTo(hipOriginator))
        .withHeader("X-Receipt-Date", matching(".+"))
        .withHeader("X-Transmitting-System", equalTo("HIP"))
        .withRequestBody(
          equalToJson(createJsonPayload(member).toString())
        )
    )

  def stubIntegrationPlatformResponse(
    status: Int,
    body: String = ""
  ): Unit =
    server.stubFor(
      post(urlEqualTo(integrationPlatformPath))
        .willReturn(
          aResponse()
            .withStatus(status)
            .withHeader("Content-Type", "application/json")
            .withBody(body)
        )
    )

  /*
    Fold this back into the main set of tests in the trait when DES is removed
   */
  "HIP Connector integration" when {
    "having an http response with mismatched status or future" should {
      s"handle failure JSON returned with HTTP 200 as an Internal Server Error" in {

        given formatF: OFormat[ResidencyStatusFailure] =
          ResidencyStatusFormats.failureFormats

        val individualDetails =
          IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

        val failureResponse =
          ResidencyStatusFailure(
            "STATUS_UNAVAILABLE",
            "Cannot provide a residency status for this pension scheme member."
          )

        val errorResponse = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")

        stubIntegrationPlatformResponse(200, Json.toJson(failureResponse).toString())

        val result = await(integrationPlatformConnector.getResidencyStatus(individualDetails, userId))

        result shouldBe Right(errorResponse)
        verifyIntegrationPlatformPost(individualDetails, expectedCount = 3)
      }
    }
  }

}
