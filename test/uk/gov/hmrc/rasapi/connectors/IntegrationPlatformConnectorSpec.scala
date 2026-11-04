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

import org.mockito.ArgumentMatchers.*
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsObject, JsValue, Json, OFormat}
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.http.*
import uk.gov.hmrc.http.client.{HttpClientV2, RequestBuilder}
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.bootstrap.http.HttpClientV2Provider
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.models.*
import uk.gov.hmrc.rasapi.services.AuditService

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.{ExecutionContext, Future}

trait IntegrationPlatformConnectorSpec
    extends AnyWordSpecLike with Matchers with GuiceOneAppPerSuite with BeforeAndAfter with MockitoSugar {

  def isHip5161Enabled: Boolean

  given hc: HeaderCarrier = HeaderCarrier()

  val mockHttp: HttpClientV2Provider            = mock[HttpClientV2Provider]
  val mockHttpClient: HttpClientV2              = mock[HttpClientV2]
  val mockRequestBuilder: RequestBuilder        = mock[RequestBuilder]
  val mockAuditService: AuditService            = mock[AuditService]
  given format: OFormat[ResidencyStatusSuccess] = ResidencyStatusFormats.successFormats

  val servicesConfig: ServicesConfig = app.injector.instanceOf[ServicesConfig]
  val appContext: AppContext         = app.injector.instanceOf[AppContext]

  lazy val integrationPlatform: String = if (isHip5161Enabled) "HIP" else "DES"

  before {
    reset(mockHttp)
    reset(mockHttpClient)
    when(mockHttp.get()).thenReturn(mockHttpClient)
    when(mockHttpClient.post(any())(any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.setHeader(any())).thenReturn(mockRequestBuilder)
    when(mockRequestBuilder.withBody(any[JsObject])(any(), any(), any())).thenReturn(mockRequestBuilder)
  }

  val uuid = "123e4567-e89b-42d3-a456-556642440000"

  def desConnector(
    allowNoNextYearStatusP: Boolean,
    isRetryEnabledP: Boolean,
    isBulkRetryEnabledP: Boolean
  ): DesConnector =
    new DesConnector(mockHttp, mockAuditService, appContext)(using ExecutionContext.global) {
      override lazy val integrationPlatformUri         = "http://localhost:9669"
      override lazy val allowNoNextYearStatus: Boolean = allowNoNextYearStatusP
      override lazy val retryLimit: Int                = 3
      override lazy val retryDelay: Int                = 500
      override lazy val desUrlHeaderEnv: String        = "DES HEADER"
      override lazy val desAuthToken: String           = "DES AUTH TOKEN"
      override lazy val isRetryEnabled: Boolean        = isRetryEnabledP
      override lazy val isBulkRetryEnabled: Boolean    = isBulkRetryEnabledP

      override def generateNewUUID: String = uuid
    }

  def hipConnector(
    allowNoNextYearStatusP: Boolean,
    isRetryEnabledP: Boolean,
    isBulkRetryEnabledP: Boolean
  ): HipConnector =
    new HipConnector(mockHttp, mockAuditService, appContext)(using ExecutionContext.global) {
      override lazy val integrationPlatformUri         = "http://localhost:9669"
      override lazy val allowNoNextYearStatus: Boolean = allowNoNextYearStatusP
      override lazy val retryLimit: Int                = 3
      override lazy val retryDelay: Int                = 500
      override lazy val isRetryEnabled: Boolean        = isRetryEnabledP
      override lazy val isBulkRetryEnabled: Boolean    = isBulkRetryEnabledP

      override def generateNewUUID: String = uuid
    }

  object IntegrationPlatformConnector {

    def makeSuccessResponse(
      nino: String,
      deathDate: Option[String],
      deathDateStatus: Option[String],
      deceasedIndicator: Option[Boolean],
      currentYearResidencyStatus: String,
      nextYearResidencyStatus: Option[String]
    ): ResidencyStatusSuccess = {

      val residencyStatusSuccess = ResidencyStatusSuccess(
        nino,
        deathDate,
        deathDateStatus,
        deceasedIndicator,
        currentYearResidencyStatus,
        nextYearResidencyStatus
      )

      if (isHip5161Enabled)
        residencyStatusSuccess.copy(statusCheckTimeStamp = Some("2027-08-24T14:15:22Z"))

      residencyStatusSuccess
    }

    def getResidencyStatus(
      individualDetails: IndividualDetails,
      userId: String
    ): Future[Either[ResidencyStatus, ResidencyStatusFailure]] =
      getResidencyStatus(
        individualDetails,
        userId,
        isBulkRequest = false,
        allowNoNextYearStatusP = true,
        isRetryEnabledP = true,
        isBulkRetryEnabledP = false
      )

    def getResidencyStatus(
      individualDetails: IndividualDetails,
      userId: String,
      isBulkRequest: Boolean,
      allowNoNextYearStatusP: Boolean,
      isRetryEnabledP: Boolean,
      isBulkRetryEnabledP: Boolean
    ): Future[Either[ResidencyStatus, ResidencyStatusFailure]] =
      if (isHip5161Enabled)
        hipConnector(allowNoNextYearStatusP, isRetryEnabledP, isBulkRetryEnabledP)
          .getResidencyStatus(individualDetails, userId, isBulkRequest)
      else
        desConnector(allowNoNextYearStatusP, isRetryEnabledP, isBulkRetryEnabledP)
          .getResidencyStatus(individualDetails, userId, isBulkRequest)

    def error_InternalServerError(
      allowNoNextYearStatusP: Boolean,
      isRetryEnabledP: Boolean,
      isBulkRetryEnabledP: Boolean
    ): String =
      if (isHip5161Enabled)
        hipConnector(allowNoNextYearStatusP, isRetryEnabledP, isBulkRetryEnabledP).error_InternalServerError
      else
        desConnector(allowNoNextYearStatusP, isRetryEnabledP, isBulkRetryEnabledP).error_InternalServerError

  }

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

  s"$integrationPlatform Connector getResidencyStatus with matching" should {

    "handle successful response when 200 is returned from integrationPlatformRouter and CY and CYPlusOne is present" in {

      val successresponse = IntegrationPlatformConnector.makeSuccessResponse(
        nino = "AB123456C",
        deathDate = Some(""),
        deathDateStatus = Some(""),
        deceasedIndicator = Some(false),
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = Some("Scottish")
      )

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

      val expectedResponse: Future[HttpResponse] =
        Future.successful(HttpResponse(200, Json.toJson(successresponse).toString()))

      when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any())).thenReturn(expectedResponse)

      val result = await(IntegrationPlatformConnector.getResidencyStatus(individualDetails, userId))
      result.isLeft                       shouldBe true
      result.left.getOrElse("Get Failed") shouldBe ResidencyStatus("otherUKResident", Some("scotResident"))

      verify(mockHttpClient, times(1)).post(any())(any())
    }

    "When the requested API version is V2.0" should {

      "handle successful response when 200 is returned from integrationPlatformRouter where CY and CYPlusOne is present for a Welsh resident" in {

        val successResponse = IntegrationPlatformConnector.makeSuccessResponse(
          nino = "AA246255",
          deathDate = Some(""),
          deathDateStatus = Some(""),
          deceasedIndicator = Some(false),
          currentYearResidencyStatus = "Welsh",
          nextYearResidencyStatus = Some("Welsh")
        )

        val individualDetails =
          IndividualDetails("AB123456C", "JACK", "OSCAR", LocalDate.parse("1962-07-07", formatter))

        val expectedResponse: Future[HttpResponse] =
          Future.successful(HttpResponse(200, Json.toJson(successResponse).toString()))

        when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any())).thenReturn(expectedResponse)

        val result = await(IntegrationPlatformConnector.getResidencyStatus(individualDetails, userId))
        result shouldBe Left(ResidencyStatus("welshResident", Some("welshResident")))

        verify(mockHttpClient, times(1)).post(any())(any())
      }

      "handle successful response when 200 is returned from integrationPlatformRouter where CY and CYPlusOne is present for a Scottish resident moving in Wales" in {

        val successResponse = IntegrationPlatformConnector.makeSuccessResponse(
          nino = "SG123480",
          deathDate = Some(""),
          deathDateStatus = Some(""),
          deceasedIndicator = Some(false),
          currentYearResidencyStatus = "Welsh",
          nextYearResidencyStatus = Some("Scottish")
        )

        val individualDetails =
          IndividualDetails("SG123480", "HELEN", "SMITH", LocalDate.parse("1975-02-18", formatter))

        val expectedResponse: Future[HttpResponse] =
          Future.successful(HttpResponse(200, Json.toJson(successResponse).toString()))

        when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any())).thenReturn(expectedResponse)

        val result = await(IntegrationPlatformConnector.getResidencyStatus(individualDetails, userId))
        result shouldBe Left(ResidencyStatus("welshResident", Some("scotResident")))

        verify(mockHttpClient, times(1)).post(any())(any())
      }

    }

    "handle successful response when 200 is returned from integrationPlatformRouter and only CY is present and feature toggle is turned on" in {

      val successResponse = IntegrationPlatformConnector.makeSuccessResponse(
        nino = "AB123456C",
        deathDate = Some(""),
        deathDateStatus = Some(""),
        deceasedIndicator = None,
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = None
      )

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

      val expectedResponse: Future[HttpResponse] =
        Future.successful(HttpResponse(200, Json.toJson(successResponse).toString()))

      when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any())).thenReturn(expectedResponse)

      val result = await(IntegrationPlatformConnector.getResidencyStatus(individualDetails, userId))
      result.isLeft                       shouldBe true
      result.left.getOrElse("Get Failed") shouldBe ResidencyStatus("otherUKResident", None)

      verify(mockHttpClient, times(1)).post(any())(any())
    }

    "handle unsuccessful response for bulk request when 429 is returned from integrationPlatformRouter the first time around and CY and " +
      "CYPlusOne is present and bulk retry is true but retry enabled is false" in {

        given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats

        val errorResponse   = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")
        val successResponse = IntegrationPlatformConnector.makeSuccessResponse(
          nino = "AB123456C",
          deathDate = Some(""),
          deathDateStatus = Some(""),
          deceasedIndicator = Some(false),
          currentYearResidencyStatus = "Uk",
          nextYearResidencyStatus = Some("Scottish")
        )

        val individualDetails =
          IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

        val expectedSuccessResponse: Future[HttpResponse] =
          Future.successful(HttpResponse(200, Json.toJson(successResponse).toString()))
        val expectedErrorResponse: Future[HttpResponse]   =
          Future.successful(HttpResponse(429, Json.toJson(errorResponse).toString()))

        when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any()))
          .thenReturn(expectedErrorResponse, expectedSuccessResponse)

        val result = await(
          IntegrationPlatformConnector.getResidencyStatus(
            individualDetails,
            userId,
            isBulkRequest = true,
            allowNoNextYearStatusP = true,
            isRetryEnabledP = false,
            isBulkRetryEnabledP = true
          )
        )

        result.isRight                      shouldBe false
        result.left.getOrElse("Get Failed") shouldBe ResidencyStatus("otherUKResident", Some("scotResident"))

        verify(mockHttpClient, times(2)).post(any())(any())
      }

    "handle unsuccessful response for bulk request when 429 is returned from integrationPlatformRouter the first time around and CY and " +
      "CYPlusOne is present and bulk retry is false and retry enabled is false" in {

        given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats

        val errorResponse = ResidencyStatusFailure("TOO_MANY_REQUESTS", "Too Many Requests.")

        val individualDetails                           =
          IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))
        val expectedErrorResponse: Future[HttpResponse] =
          Future.successful(HttpResponse(429, Json.toJson(errorResponse).toString()))

        when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any())).thenReturn(expectedErrorResponse)

        val result = await(
          IntegrationPlatformConnector.getResidencyStatus(
            individualDetails,
            userId,
            isBulkRequest = true,
            allowNoNextYearStatusP = true,
            isRetryEnabledP = false,
            isBulkRetryEnabledP = false
          )
        )

        result.isLeft                  shouldBe false
        result.getOrElse("Get Failed") shouldBe errorResponse

        verify(mockHttpClient, times(1)).post(any())(any())
      }

    "handle successful response when 200 is returned from integrationPlatformRouter and only CY is present and no next year status toggle is turned off" in {

      val errorResponse = ResidencyStatusFailure(
        IntegrationPlatformConnector
          .error_InternalServerError(
            allowNoNextYearStatusP = false,
            isRetryEnabledP = true,
            isBulkRetryEnabledP = false
          ),
        "Internal server error."
      )

      val successResponse   = IntegrationPlatformConnector.makeSuccessResponse(
        nino = "AB123456C",
        deathDate = Some(""),
        deathDateStatus = Some(""),
        deceasedIndicator = Some(false),
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = None
      )
      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", LocalDate.parse("1990-02-21", formatter))

      when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any()))
        .thenReturn(Future.successful(HttpResponse(200, Json.toJson(successResponse).toString())))
      val result = await(
        IntegrationPlatformConnector.getResidencyStatus(
          individualDetails,
          userId,
          isBulkRequest = false,
          allowNoNextYearStatusP = false,
          isRetryEnabledP = true,
          isBulkRetryEnabledP = false
        )
      )
      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe errorResponse
      verify(mockHttpClient, times(1)).post(any())(any())
    }

    "handle failure response (no match) from integrationPlatformRouter" in {
      given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats
      val errorResponse                              = ResidencyStatusFailure(
        "STATUS_UNAVAILABLE",
        "Cannot provide a residency status for this pension scheme member."
      )

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any()))
        .thenReturn(Future.successful(HttpResponse(404, Json.toJson(errorResponse).toString())))
      val result = await(IntegrationPlatformConnector.getResidencyStatus(individualDetails, userId))

      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe errorResponse

      verify(mockHttpClient, times(1)).post(any())(any())
    }
    "handle forbidden response from integrationPlatformRouter" in {

      val errorResponse = ResidencyStatusFailure(
        appContext.matchingFailedStatus,
        "Cannot provide a residency status for this pension scheme member."
      )

      val individualDetails =
        IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("STATUS_UNAVAILABLE", 403, 403)))

      val result = await(IntegrationPlatformConnector.getResidencyStatus(individualDetails, userId))
      println(result)
      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe errorResponse
    }
    "handle success response but the person is deceased from integrationPlatformRouter" in {
      val successResponse = IntegrationPlatformConnector.makeSuccessResponse(
        nino = "AB123456C",
        deathDate = Some("2017-12-25"),
        deathDateStatus = None,
        deceasedIndicator = Some(true),
        currentYearResidencyStatus = "Uk",
        nextYearResidencyStatus = Some("Uk")
      )

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any()))
        .thenReturn(Future.successful(HttpResponse(200, Json.toJson(successResponse).toString())))

      val expectedResult =
        ResidencyStatusFailure("DECEASED", "Cannot provide a residency status for this pension scheme member.")

      val result = await(IntegrationPlatformConnector.getResidencyStatus(individualDetails, userId))

      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe expectedResult

      verify(mockHttpClient, times(1)).post(any())(any())
    }

    "handle unexpected responses as 500 from integrationPlatformRouter" in {
      given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats
      val errorResponse                              = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any()))
        .thenReturn(Future.successful(HttpResponse(500, Json.toJson(errorResponse).toString())))

      val result = await(IntegrationPlatformConnector.getResidencyStatus(individualDetails, userId))

      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe errorResponse

      verify(mockHttpClient, times(3)).post(any())(any())
    }

    "handle Service Unavailable (503) response from integrationPlatformRouter" in {
      val errorResponse = ResidencyStatusFailure("SERVICE_UNAVAILABLE", "Service unavailable")

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any()))
        .thenReturn(Future.failed(UpstreamErrorResponse("SERVICE_UNAVAILABLE", 503, 503)))

      val result = await(IntegrationPlatformConnector.getResidencyStatus(individualDetails, userId))

      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe errorResponse

      verify(mockHttpClient, times(3)).post(any())(any())
    }

    "handle bad request from integrationPlatformRouter" in {
      given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats
      val expectedErrorResponse                      = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")
      val errorResponse                              = ResidencyStatusFailure("DO_NOT_RE_PROCESS", "Internal server error.")

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

      when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any()))
        .thenReturn(Future.successful(HttpResponse(400, Json.toJson(errorResponse).toString())))

      val result = await(IntegrationPlatformConnector.getResidencyStatus(individualDetails, userId))

      result.isLeft                  shouldBe false
      result.getOrElse("Get Failed") shouldBe expectedErrorResponse

      verify(mockHttpClient, times(1)).post(any())(any())
    }

    "Handle requests" when {
      "it cannot be processed the first time round" in {

        given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats
        val errorResponse                              = ResidencyStatusFailure("TOO_MANY_REQUESTS", "Too Many Requests.")
        val successResponse                            = IntegrationPlatformConnector.makeSuccessResponse(
          nino = "AB123456C",
          deathDate = Some(""),
          deathDateStatus = Some(""),
          deceasedIndicator = Some(false),
          currentYearResidencyStatus = "Uk",
          nextYearResidencyStatus = None
        )

        val individualDetails =
          IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

        when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any())).thenReturn(
          Future.successful(HttpResponse(429, Json.toJson(errorResponse).toString())),
          Future.successful(HttpResponse(200, Json.toJson(successResponse).toString()))
        )

        val result = await(IntegrationPlatformConnector.getResidencyStatus(individualDetails, userId))

        result.left.getOrElse("Get Failed") shouldBe ResidencyStatus("otherUKResident")
        result.isRight                      shouldBe false

        verify(mockHttpClient, times(2)).post(any())(any())
      }

      "429 (Too Many Requests) has been returned 3 times" in {

        given formatF: OFormat[ResidencyStatusFailure] = ResidencyStatusFormats.failureFormats

        val errorResponse = ResidencyStatusFailure("TOO_MANY_REQUESTS", "Too Many Requests.")

        val individualDetails =
          IndividualDetails("AB123456C", "JOHN", "Lewis", LocalDate.parse("1990-02-21", formatter))

        when(mockRequestBuilder.execute(any[HttpReads[HttpResponse]], any()))
          .thenReturn(Future.successful(HttpResponse(429, Json.toJson(errorResponse).toString())))

        val result = await(IntegrationPlatformConnector.getResidencyStatus(individualDetails, userId))
        result.isLeft                  shouldBe false
        result.getOrElse("Get Failed") shouldBe errorResponse

        verify(mockHttpClient, times(3)).post(any())(any())
      }
    }
  }

}

class DesConnectorSpec extends IntegrationPlatformConnectorSpec {
  def isHip5161Enabled: Boolean = false

  val testDesConnector: DesConnector =
    desConnector(allowNoNextYearStatusP = true, isRetryEnabledP = true, isBulkRetryEnabledP = false)

  "DESConnector correlationID" when {
    "requestID is present in the headerCarrier" should {
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
    }

    "requestID is not present in the headerCarrier should return a new ID" in {
      testDesConnector.correlationId(using HeaderCarrier()) shouldBe uuid
    }
  }

}

class HipConnectorSpec extends IntegrationPlatformConnectorSpec {
  def isHip5161Enabled: Boolean = true
}
