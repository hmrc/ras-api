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

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers._
import org.mockito.ArgumentMatchers.{eq => Meq}
import org.mockito.Mockito.{reset, times, verify, when}
import org.scalatest.BeforeAndAfter
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.json.{JsValue, Json, OFormat}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.bootstrap.http.DefaultHttpClient
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.models._
import uk.gov.hmrc.rasapi.services.AuditService

import scala.concurrent.{ExecutionContext, Future}

class DesConnectorSpec extends UnitSpec with GuiceOneAppPerSuite with BeforeAndAfter with MockitoSugar {

  implicit val hc = HeaderCarrier()

  val mockHttp: DefaultHttpClient = mock[DefaultHttpClient]
  val mockAuditService: AuditService = mock[AuditService]
  implicit val format: OFormat[ResidencyStatusSuccess] = ResidencyStatusFormats.successFormats

  val appContext: AppContext = app.injector.instanceOf[AppContext]

  before {
    reset(mockHttp)
  }

  object TestDesConnector extends DesConnector(mockHttp, mockAuditService, appContext, ExecutionContext.global) {
    override lazy val desBaseUrl = ""
    override lazy val edhUrl: String = "test-url"
    override val auditService: AuditService = mockAuditService
    override lazy val allowNoNextYearStatus: Boolean = true
    override lazy val retryLimit: Int = 3
    override lazy val retryDelay: Int = 500
    override lazy val desUrlHeaderEnv: String = "DES HEADER"
    override lazy val desAuthToken: String = "DES AUTH TOKEN"
    override lazy val isRetryEnabled: Boolean = true
    override lazy val isBulkRetryEnabled: Boolean = false
  }

  val individualDetails = IndividualDetails("LE241131B", "Joe", "Bloggs", new DateTime("1990-12-03"))
  val userId = "A123456"

  val residencyStatus = ResidencyStatus(currentYearResidencyStatus = "scotResident",
    nextYearForecastResidencyStatus = Some("scotResident"))

  val residencyStatusFailure = ResidencyStatusFailure("500", "HODS NOT AVAILABLE")

  val residencyStatusJson = Json.parse(
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
       |  "dob": "${individualDetails.dateOfBirth.toString("yyyy-MM-dd")}",
       |  "pensionSchemeOrganisationID": "${userId}"
       |}
    """.stripMargin
  )

  "DESConnector getResidencyStatus with matching" should {

    "handle successful response when 200 is returned from des and CY and CYPlusOne is present" in {

      val successresponse = ResidencyStatusSuccess(nino = "AB123456C", deathDate = Some(""), deathDateStatus = Some(""), deseasedIndicator = Some(false),
        currentYearResidencyStatus = "Uk", nextYearResidencyStatus = Some("Scottish"))

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", new DateTime("1990-02-21"))

      val expectedPayload = createJsonPayload(individualDetails)

      when(mockHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(200, Some(Json.toJson(successresponse)))))

      val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId, V2_0))
      result.isLeft shouldBe true
      result.left.get shouldBe ResidencyStatus("otherUKResident", Some("scotResident"))

      verify(mockHttp, times(1)).POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())
    }

    "When the requested API version is V1.0" should {

      "handle successful response when 200 is returned from des where CY and CYPlusOne is present for a Welsh resident" in {

        val successResponse = ResidencyStatusSuccess(nino = "AA246255", deathDate = Some(""), deathDateStatus = Some(""), deseasedIndicator = Some(false),
          currentYearResidencyStatus = "Welsh", nextYearResidencyStatus = Some("Welsh"))

        val individualDetails = IndividualDetails("AB123456C", "JACK", "OSCAR", new DateTime("1962-07-07"))

        val expectedPayload = createJsonPayload(individualDetails)

        when(mockHttp.POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())).
          thenReturn(Future.successful(HttpResponse(200, Some(Json.toJson(successResponse)))))

        val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId, V1_0))
        result shouldBe Left(ResidencyStatus("otherUKResident", Some("otherUKResident")))

        verify(mockHttp, times(1)).POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())
      }

      "handle successful response when 200 is returned from des where CY and CYPlusOne is present for a Scottish resident moving in Wales" in {

        val successResponse = ResidencyStatusSuccess(nino = "SG123480", deathDate = Some(""), deathDateStatus = Some(""), deseasedIndicator = Some(false),
          currentYearResidencyStatus = "Welsh", nextYearResidencyStatus = Some("Scottish"))

        val individualDetails = IndividualDetails("SG123480", "HELEN", "SMITH", new DateTime("1975-02-18"))

        val expectedPayload = createJsonPayload(individualDetails)

        when(mockHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
          thenReturn(Future.successful(HttpResponse(200, Some(Json.toJson(successResponse)))))

        val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId, V1_0))
        result shouldBe Left(ResidencyStatus("otherUKResident", Some("scotResident")))

        verify(mockHttp, times(1)).POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())
      }

    }

    "When the requested API version is V2.0" should {

      "handle successful response when 200 is returned from des where CY and CYPlusOne is present for a Welsh resident" in {

        val successResponse = ResidencyStatusSuccess(nino = "AA246255", deathDate = Some(""), deathDateStatus = Some(""), deseasedIndicator = Some(false),
          currentYearResidencyStatus = "Welsh", nextYearResidencyStatus = Some("Welsh"))

        val individualDetails = IndividualDetails("AB123456C", "JACK", "OSCAR", new DateTime("1962-07-07"))

        val expectedPayload = createJsonPayload(individualDetails)

        when(mockHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
          thenReturn(Future.successful(HttpResponse(200, Some(Json.toJson(successResponse)))))

        val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId, V2_0))
        result shouldBe Left(ResidencyStatus("welshResident", Some("welshResident")))

        verify(mockHttp, times(1)).POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())
      }

      "handle successful response when 200 is returned from des where CY and CYPlusOne is present for a Scottish resident moving in Wales" in {

        val successResponse = ResidencyStatusSuccess(nino = "SG123480", deathDate = Some(""), deathDateStatus = Some(""), deseasedIndicator = Some(false),
          currentYearResidencyStatus = "Welsh", nextYearResidencyStatus = Some("Scottish"))

        val individualDetails = IndividualDetails("SG123480", "HELEN", "SMITH", new DateTime("1975-02-18"))

        val expectedPayload = createJsonPayload(individualDetails)

        when(mockHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
          thenReturn(Future.successful(HttpResponse(200, Some(Json.toJson(successResponse)))))

        val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId, V2_0))
        result shouldBe Left(ResidencyStatus("welshResident", Some("scotResident")))

        verify(mockHttp, times(1)).POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())
      }

    }

    "handle successful response when 200 is returned from des and only CY is present and feature toggle is turned on" in {

      val successresponse = ResidencyStatusSuccess(nino = "AB123456C", deathDate = Some(""), deathDateStatus = Some(""), deseasedIndicator = None,
        currentYearResidencyStatus = "Uk", nextYearResidencyStatus = None)

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", new DateTime("1990-02-21"))

      val expectedPayload = createJsonPayload(individualDetails)

      when(mockHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(200, Some(Json.toJson(successresponse)))))

      val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId, V2_0))
      result.isLeft shouldBe true
      result.left.get shouldBe ResidencyStatus("otherUKResident", None)

      verify(mockHttp, times(1)).POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())
    }

    "handle unsuccessful response for bulk request when 429 is returned from des the first time around and CY and " +
      "CYPlusOne is present and bulk retry is true but retry enabled is false" in {

      implicit val formatF = ResidencyStatusFormats.failureFormats

      object TestDesConnector extends DesConnector(mockHttp, mockAuditService, appContext, ExecutionContext.global) {
        override lazy val desBaseUrl = ""
        override lazy val edhUrl: String = "test-url"
        override val auditService: AuditService = mockAuditService
        override lazy val allowNoNextYearStatus: Boolean = true
        override lazy val retryLimit: Int = 3
        override lazy val retryDelay: Int = 500
        override lazy val desUrlHeaderEnv: String = "DES HEADER"
        override lazy val desAuthToken: String = "DES AUTH TOKEN"
        override lazy val isRetryEnabled: Boolean = false
        override lazy val isBulkRetryEnabled: Boolean = true
      }

      val errorResponse = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")
      val successresponse = ResidencyStatusSuccess(nino = "AB123456C", deathDate = Some(""), deathDateStatus = Some(""), deseasedIndicator = Some(false),
        currentYearResidencyStatus = "Uk", nextYearResidencyStatus = Some("Scottish"))

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", new DateTime("1990-02-21"))

      val expectedPayload = createJsonPayload(individualDetails)

      when(mockHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(429, Some(Json.toJson(errorResponse)))),
          Future.successful(HttpResponse(200, Some(Json.toJson(successresponse)))))

      val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId, V2_0, isBulkRequest = true))

      result.isRight shouldBe false
      result.left.get shouldBe ResidencyStatus("otherUKResident", Some("scotResident"))

      verify(mockHttp, times(2)).POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())
    }

    "handle unsuccessful response for bulk request when 429 is returned from des the first time around and CY and " +
      "CYPlusOne is present and bulk retry is false and retry enabled is false" in {

      implicit val formatF = ResidencyStatusFormats.failureFormats

      object TestDesConnector extends DesConnector(mockHttp, mockAuditService, appContext, ExecutionContext.global) {
        override lazy val desBaseUrl = ""
        override lazy val edhUrl: String = "test-url"
        override val auditService: AuditService = mockAuditService
        override lazy val allowNoNextYearStatus: Boolean = true
        override lazy val retryLimit: Int = 3
        override lazy val retryDelay: Int = 500
        override lazy val desUrlHeaderEnv: String = "DES HEADER"
        override lazy val desAuthToken: String = "DES AUTH TOKEN"
        override lazy val isRetryEnabled: Boolean = false
        override lazy val isBulkRetryEnabled: Boolean = false
      }

      val errorResponse = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")
      val successresponse = ResidencyStatusSuccess(nino = "AB123456C", deathDate = Some(""), deathDateStatus = Some(""), deseasedIndicator = Some(false),
        currentYearResidencyStatus = "Uk", nextYearResidencyStatus = Some("Scottish"))

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", new DateTime("1990-02-21"))

      val expectedPayload = createJsonPayload(individualDetails)

      when(mockHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(429, Some(Json.toJson(errorResponse)))))

      val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId, V2_0, isBulkRequest = true))

      result.isLeft shouldBe false
      result.right.get shouldBe errorResponse

      verify(mockHttp, times(1)).POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())
    }

    "handle successful response when 200 is returned from des and only CY is present and no next year status toggle is turned off" in {

      object TestDesConnector extends DesConnector(mockHttp, mockAuditService, appContext, ExecutionContext.global) {
        override lazy val desBaseUrl = ""
        override lazy val edhUrl: String = "test-url"
        override val auditService: AuditService = mockAuditService
        override lazy val allowNoNextYearStatus: Boolean = false
        override lazy val retryLimit: Int = 3
        override lazy val retryDelay: Int = 500
        override lazy val desUrlHeaderEnv: String = "DES HEADER"
        override lazy val desAuthToken: String = "DES AUTH TOKEN"
        override lazy val isRetryEnabled: Boolean = true
        override lazy val isBulkRetryEnabled: Boolean = false
      }

      val errorResponse = ResidencyStatusFailure(TestDesConnector.error_InternalServerError, "Internal server error.")

      val successResponse = ResidencyStatusSuccess(nino = "AB123456C", deathDate = Some(""), deathDateStatus = Some(""), deseasedIndicator = Some(false),
        currentYearResidencyStatus = "Uk", nextYearResidencyStatus = None)

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "SMITH", new DateTime("1990-02-21"))

      val expectedPayload = createJsonPayload(individualDetails)

      when(mockHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(200, Some(Json.toJson(successResponse)))))

      val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId, V2_0))

      result.isLeft shouldBe false
      result.right.get shouldBe errorResponse

      verify(mockHttp, times(1)).POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())
    }

    "handle failure response (no match) from des" in {
      implicit val formatF = ResidencyStatusFormats.failureFormats
      val errorResponse = ResidencyStatusFailure("STATUS_UNAVAILABLE", "Cannot provide a residency status for this pension scheme member.")

      val individualDetails =IndividualDetails("AB123456C", "JOHN", "Lewis", new DateTime("1990-02-21"))

      val expectedPayload = createJsonPayload(individualDetails)

      when(mockHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(404, Some(Json.toJson(errorResponse)))))

      val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId, V2_0))

      result.isLeft shouldBe false
      result.right.get shouldBe errorResponse

      verify(mockHttp, times(1)).POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())
    }

    "handle success response but the person is deceased from des" in {
      val successResponse = ResidencyStatusSuccess(nino = "AB123456C", deathDate = Some("2017-12-25"), deathDateStatus = None,
        deseasedIndicator = Some(true), currentYearResidencyStatus = "Uk", nextYearResidencyStatus = Some("Uk"))

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", new DateTime("1990-02-21"))

      val expectedPayload = createJsonPayload(individualDetails)

      when(mockHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(200, Some(Json.toJson(successResponse)))))

      val expectedResult = ResidencyStatusFailure("DECEASED", "Cannot provide a residency status for this pension scheme member.")

      val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId, V2_0))

      result.isLeft shouldBe false
      result.right.get shouldBe expectedResult

      verify(mockHttp, times(1)).POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())
    }

    "handle unexpected responses as 500 from des" in {
      implicit val formatF = ResidencyStatusFormats.failureFormats
      val errorResponse = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", new DateTime("1990-02-21"))

      val expectedPayload = createJsonPayload(individualDetails)

      when(mockHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(500, Some(Json.toJson(errorResponse)))))

      val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId, V2_0))

      result.isLeft shouldBe false
      result.right.get shouldBe errorResponse

      verify(mockHttp, times(3)).POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())
    }

    "handle Service Unavailable (503) response from des" in {
      implicit val formatF = ResidencyStatusFormats.failureFormats
      val errorResponse = ResidencyStatusFailure("SERVICE_UNAVAILABLE", "Service unavailable")

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", new DateTime("1990-02-21"))

      val expectedPayload = createJsonPayload(individualDetails)

      when(mockHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
        thenReturn(Future.failed(Upstream5xxResponse("SERVICE_UNAVAILABLE", 503, 503)))

      val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId, V2_0))

      result.isLeft shouldBe false
      result.right.get shouldBe errorResponse

      verify(mockHttp, times(3)).POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())
    }

    "handle bad request from des" in {
      implicit val formatF = ResidencyStatusFormats.failureFormats
      val expectedErrorResponse = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")
      val errorResponse = ResidencyStatusFailure("DO_NOT_RE_PROCESS", "Internal server error.")

      val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", new DateTime("1990-02-21"))

      val expectedPayload = createJsonPayload(individualDetails)

      when(mockHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
        thenReturn(Future.successful(HttpResponse(400, Some(Json.toJson(errorResponse)))))

      val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId, V2_0))

      result.isLeft shouldBe false
      result.right.get shouldBe expectedErrorResponse

      verify(mockHttp, times(1)).POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())
    }

    "Handle requests" when {
      "it cannot be processed the first time round" in {

        implicit val formatF = ResidencyStatusFormats.failureFormats
        val errorResponse = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "Internal server error.")
        val successresponse = ResidencyStatusSuccess(nino = "AB123456C", deathDate = Some(""),
          deathDateStatus = Some(""), deseasedIndicator = Some(false),
          currentYearResidencyStatus = "Uk", nextYearResidencyStatus = None)

        val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", new DateTime("1990-02-21"))

        val expectedPayload = createJsonPayload(individualDetails)

        when(mockHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
          thenReturn(Future.successful(HttpResponse(429, Some(Json.toJson(errorResponse)))),
            Future.successful(HttpResponse(200, Some(Json.toJson(successresponse)))))

        val result = await(TestDesConnector.getResidencyStatus(
          individualDetails, userId, V2_0))

        result.left.get shouldBe ResidencyStatus("otherUKResident")
        result.isRight shouldBe false

        verify(mockHttp, times(2)).POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())
      }

      "429 (Too Many Requests) has been returned 3 times" in {

        implicit val formatF = ResidencyStatusFormats.failureFormats

        val errorResponse = ResidencyStatusFailure("TOO_MANY_REQUESTS", "Too many requests received")

        val individualDetails = IndividualDetails("AB123456C", "JOHN", "Lewis", new DateTime("1990-02-21"))

        val expectedPayload = createJsonPayload(individualDetails)

        when(mockHttp.POST[JsValue, HttpResponse](any(), any(), any())(any(), any(), any(), any())).
          thenReturn(Future.successful(HttpResponse(429, Some(Json.toJson(errorResponse)))))

        val result = await(TestDesConnector.getResidencyStatus(individualDetails, userId, V2_0))
        result.isLeft shouldBe false
        result.right.get shouldBe errorResponse

        verify(mockHttp, times(3)).POST[JsValue, HttpResponse](any(), Meq[JsValue](expectedPayload), any())(any(), any(), any(), any())
      }
    }
  }
}
