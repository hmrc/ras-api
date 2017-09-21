/*
 * Copyright 2017 HM Revenue & Customs
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

package uk.gov.hmrc.rasapi.controllers

import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.rasapi.connectors.CachingConnector
import org.mockito.Matchers.{eq => Meq, _}
import org.mockito.Mockito._
import org.scalatest.{BeforeAndAfter, ShouldMatchers, WordSpec}
import uk.gov.hmrc.auth.core.ConfidenceLevel.L300
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpResponse, NotFoundException}
import uk.gov.hmrc.rasapi.config.RasAuthConnector
import uk.gov.hmrc.rasapi.models._
import uk.gov.hmrc.rasapi.services.{AuditService, HttpResponseHandlerService}

import scala.concurrent.Future

class LookupControllerSpec extends WordSpec with MockitoSugar with ShouldMatchers with OneAppPerSuite with BeforeAndAfter{

  implicit val hc = HeaderCarrier()

  val acceptHeader: (String, String) = (HeaderNames.ACCEPT, "application/vnd.hmrc.1.0+json")

  val mockHttpResponseHandlerService = mock[HttpResponseHandlerService]
  val mockCachingConnector = mock[CachingConnector]
  val mockAuditService = mock[AuditService]
  val mockAuthConnector = mock[RasAuthConnector]

  val expectedNino: Nino = Nino("LE241131B")

  private val enrolmentIdentifier1 = EnrolmentIdentifier("PSAID", "Z123456")
  private val enrolment1 = new Enrolment(key = "HMRC-PSA-ORG", identifiers = List(enrolmentIdentifier1), state = "Activated", confidenceLevel = L300, None)
  private val enrolmentIdentifier2 = EnrolmentIdentifier("PPID", "Z123456")
  private val enrolment2 = new Enrolment(key = "HMRC-PP-ORG", identifiers = List(enrolmentIdentifier2), state = "Activated", confidenceLevel = L300, None)
  private val enrolments = new Enrolments(Set(enrolment1,enrolment2))

  val successfulRetrieval: Future[Enrolments] = Future.successful(enrolments)

  object TestLookupController extends LookupController {
    override val responseHandlerService: HttpResponseHandlerService = mockHttpResponseHandlerService
    override val cachingConnector = mockCachingConnector
    override val auditService: AuditService = mockAuditService
    override val authConnector: AuthConnector = mockAuthConnector
  }

  before{
    reset(mockAuditService)
  }

  "The lookup controller endpoint" should {

    "audit a successful lookup response" when {
      "a valid uuid has been submitted" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any())).thenReturn(successfulRetrieval)

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"
        val residencyStatus = ResidencyStatus("otherUKResident", "otherUKResident")

        when(mockCachingConnector.getCachedData(any())(any())).thenReturn(Future.successful(HttpResponse(OK, Some(Json.toJson(expectedNino)))))
        when(mockHttpResponseHandlerService.handleResidencyStatusResponse(any(), any())(any(), any())).thenReturn(Future.successful(Left(residencyStatus)))

        await(TestLookupController.getResidencyStatus(uuid)
          .apply(FakeRequest(Helpers.GET, s"/relief-at-source/customer/$uuid/residency-status")
          .withHeaders(acceptHeader)))

        verify(mockAuditService).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/relief-at-source/customer/$uuid/residency-status"),
          auditData = Meq(Map("successfulLookup" -> "true",
                              "CYStatus" -> "otherUKResident",
                              "NextCYStatus" -> "otherUKResident",
                              "nino" -> "LE241131B"))
        )(any())
      }
    }

    "audit an unsuccessful lookup response" when {
      "an invalid uuid is given" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any())).thenReturn(successfulRetrieval)

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"

        when(mockCachingConnector.getCachedData(any())(any())).thenReturn(Future.failed(new NotFoundException("")))

        await(TestLookupController.getResidencyStatus(uuid)
          .apply(FakeRequest(Helpers.GET, s"/relief-at-source/customer/$uuid/residency-status")
          .withHeaders(acceptHeader)))

        verify(mockAuditService).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/relief-at-source/customer/$uuid/residency-status"),
          auditData = Meq(Map("successfulLookup" -> "false",
                              "reason" -> "INVALID_UUID"))
        )(any())
      }

      "a problem occurred while trying to call caching service" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any())).thenReturn(successfulRetrieval)

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"

        when(mockCachingConnector.getCachedData(any())(any())).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR)))

        await(TestLookupController.getResidencyStatus(uuid)
          .apply(FakeRequest(Helpers.GET, s"/relief-at-source/customer/$uuid/residency-status")
          .withHeaders(acceptHeader)))

        verify(mockAuditService).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/relief-at-source/customer/$uuid/residency-status"),
          auditData = Meq(Map("successfulLookup" -> "false",
                              "reason" -> "INTERNAL_SERVER_ERROR"))
        )(any())
      }

      "there is corrupted data held in the Head of Duty (HoD) system" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any())).thenReturn(successfulRetrieval)

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"

        when(mockCachingConnector.getCachedData(any())(any())).thenReturn(Future.successful(HttpResponse(OK, Some(Json.toJson(expectedNino)))))
        when(mockHttpResponseHandlerService.handleResidencyStatusResponse(any(), any())(any(), any())).thenReturn(Future.successful(Right("")))

        await(TestLookupController.getResidencyStatus(uuid)
          .apply(FakeRequest(Helpers.GET, s"/relief-at-source/customer/$uuid/residency-status")
          .withHeaders(acceptHeader)))

        verify(mockAuditService).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/relief-at-source/customer/$uuid/residency-status"),
          auditData = Meq(Map("nino" -> "LE241131B",
                              "successfulLookup" -> "false",
                              "reason" -> "INTERNAL_SERVER_ERROR"))
        )(any())
      }

      "a problem occurred while trying to call the Head of Duty (HoD) system" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any())).thenReturn(successfulRetrieval)

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"

        when(mockCachingConnector.getCachedData(any())(any())).thenReturn(Future.successful(HttpResponse(OK, Some(Json.toJson(expectedNino)))))
        when(mockHttpResponseHandlerService.handleResidencyStatusResponse(any(), any())(any(), any())).thenReturn(Future.successful(Right("")))

        await(TestLookupController.getResidencyStatus(uuid)
          .apply(FakeRequest(Helpers.GET, s"/relief-at-source/customer/$uuid/residency-status")
          .withHeaders(acceptHeader)))

        verify(mockAuditService).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/relief-at-source/customer/$uuid/residency-status"),
          auditData = Meq(Map("nino" -> "LE241131B",
                              "successfulLookup" -> "false",
                              "reason" -> "INTERNAL_SERVER_ERROR"))
        )(any())
      }

      "A 403 is returned from DES Connector (HoD)" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any())).thenReturn(successfulRetrieval)

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"

        when(mockCachingConnector.getCachedData(any())(any())).thenReturn(Future.successful(HttpResponse(OK, Some(Json.toJson(expectedNino)))))
        when(mockHttpResponseHandlerService.handleResidencyStatusResponse(any(), any())(any(), any())).thenReturn(Future.successful(Right("")))

        await(TestLookupController.getResidencyStatus(uuid)
          .apply(FakeRequest(Helpers.GET, s"/relief-at-source/customer/$uuid/residency-status")
            .withHeaders(acceptHeader)))

        verify(mockAuditService).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/relief-at-source/customer/$uuid/residency-status"),
          auditData = Meq(Map("nino" -> "LE241131B",
                              "successfulLookup" -> "false",
                              "reason" -> "INTERNAL_SERVER_ERROR"))
        )(any())
      }

      "an unexpected 4xx response is returned from DES Connector (HoD)" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any())).thenReturn(successfulRetrieval)

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"

        when(mockCachingConnector.getCachedData(any())(any())).thenReturn(Future.successful(HttpResponse(OK, Some(Json.toJson(expectedNino)))))
        when(mockHttpResponseHandlerService.handleResidencyStatusResponse(any(), any())(any(), any())).thenReturn(Future.successful(Right("")))

        await(TestLookupController.getResidencyStatus(uuid)
          .apply(FakeRequest(Helpers.GET, s"/relief-at-source/customer/$uuid/residency-status")
            .withHeaders(acceptHeader)))

        verify(mockAuditService).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/relief-at-source/customer/$uuid/residency-status"),
          auditData = Meq(Map("nino" -> "LE241131B",
                              "successfulLookup" -> "false",
                              "reason" -> "INTERNAL_SERVER_ERROR"))
        )(any())
      }
    }
  }

  "LookupController" should {

    "return status 200 with correct residency status json" when {

      "a valid UUID is given" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any())).thenReturn(successfulRetrieval)

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"
        val residencyStatus = ResidencyStatus("otherUKResident", "otherUKResident")

        val expectedJsonResult = Json.parse(
          """
            {
              "currentYearResidencyStatus" : "otherUKResident",
              "nextYearForecastResidencyStatus" : "otherUKResident"
            }
          """.stripMargin)

        when(mockCachingConnector.getCachedData(any())(any())).thenReturn(Future.successful(HttpResponse(OK, Some(Json.toJson(expectedNino)))))
        when(mockHttpResponseHandlerService.handleResidencyStatusResponse(any(), any())(any(), any())).thenReturn(Future.successful(Left(residencyStatus)))

        val result = TestLookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader))

        status(result) shouldBe OK
        contentAsJson(result) shouldBe expectedJsonResult
      }
    }

    "return status 401 (Unauthorised)" when {
      "a valid lookup request has been submitted with no PSA or PP enrolments" in {

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any())).thenReturn(Future.failed(new InsufficientEnrolments))

        val authorisationHeader: (String, String) = (HeaderNames.AUTHORIZATION, "Bearer ABC")

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "UNAUTHORIZED",
            |  "message": "Supplied OAuth token not authorised to access data for given tax identifier(s)"
            |}
          """.stripMargin)

        val result = TestLookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").
          withHeaders(acceptHeader, authorisationHeader))

        status(result) shouldBe UNAUTHORIZED
        contentAsJson(result) shouldBe expectedJsonResult
      }

      "a valid lookup request has been submitted with no authorization header present" in {

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any())).thenReturn(Future.failed(new MissingBearerToken))

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INVALID_CREDENTIALS",
            |  "message": "Invalid OAuth token supplied for user-restricted or application-restricted resource (including expired token)"
            |}
          """.stripMargin)

        val result = TestLookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").
          withHeaders(acceptHeader))

        status(result) shouldBe UNAUTHORIZED
        contentAsJson(result) shouldBe expectedJsonResult
      }

      "a valid lookup request has been submitted with no value declared in the authorization header" in {

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any())).thenReturn(Future.failed(new InvalidBearerToken))

        val authorisationHeader: (String, String) = (HeaderNames.AUTHORIZATION, "")

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INVALID_CREDENTIALS",
            |  "message": "Invalid OAuth token supplied for user-restricted or application-restricted resource (including expired token)"
            |}
          """.stripMargin)

        val result = TestLookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").
          withHeaders(acceptHeader, authorisationHeader))

        status(result) shouldBe UNAUTHORIZED
        contentAsJson(result) shouldBe expectedJsonResult
      }

      "a valid lookup request has been submitted with an expired bearer token in the authorization header" in {
        // The bearer token used in this test is not valid but for purposes of testing is being treated as a valid bearer token.

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any())).thenReturn(Future.failed(new BearerTokenExpired))

        val authorisationHeader: (String, String) = (HeaderNames.AUTHORIZATION, "")

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INVALID_CREDENTIALS",
            |  "message": "Invalid OAuth token supplied for user-restricted or application-restricted resource (including expired token)"
            |}
          """.stripMargin)

        val result = TestLookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").
          withHeaders(acceptHeader, authorisationHeader))

        status(result) shouldBe UNAUTHORIZED
        contentAsJson(result) shouldBe expectedJsonResult
      }

      "a valid match request has been submitted with an invalid bearer token in the authorization header" in {

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any())).thenReturn(Future.failed(new SessionRecordNotFound))

        val authorisationHeader: (String, String) = (HeaderNames.AUTHORIZATION, "Bearer ABC")

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INVALID_CREDENTIALS",
            |  "message": "Invalid OAuth token supplied for user-restricted or application-restricted resource (including expired token)"
            |}
          """.stripMargin)

        val result = TestLookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").
          withHeaders(acceptHeader, authorisationHeader))

        status(result) shouldBe UNAUTHORIZED
        contentAsJson(result) shouldBe expectedJsonResult
      }
    }

    "return status 403" when {

      "a timed out UUID is given" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any())).thenReturn(successfulRetrieval)

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc1"

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INVALID_UUID",
            |  "message": "The match has timed out and the UUID is no longer valid. The match (POST to /match) will need to be repeated."
            |}
          """.stripMargin)

        when(mockCachingConnector.getCachedData(any())(any())).thenReturn(Future.failed(new NotFoundException("")))

        val result = TestLookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader))

        status(result) shouldBe FORBIDDEN
        contentAsJson(result) shouldBe expectedJsonResult
      }
    }

    "return status 400" when {
      "an invalid UUID is given (non conforming to regex: ^[0-9A-Fa-f]{8}(-[0-9A-Fa-f]{4}){3}-[0-9A-Fa-f]{12}$" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any())).thenReturn(successfulRetrieval)

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc"

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INVALID_FORMAT",
            |  "message": "Invalid UUID format. Use the UUID provided."
            |}
          """.stripMargin)

        val result = TestLookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader))

        status(result) shouldBe BAD_REQUEST
        contentAsJson(result) shouldBe expectedJsonResult
      }
    }

    "return status 500" when {

      when(mockAuthConnector.authorise[Enrolments](any(), any())(any())).thenReturn(successfulRetrieval)

      "something goes wrong in the caching service" in {

        val uuid: String = "76648d82-309e-484d-a310-d0ffd2997795"

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INTERNAL_SERVER_ERROR",
            |  "message": "Internal server error"
            |}
          """.stripMargin)

        when(mockCachingConnector.getCachedData(any())(any())).thenReturn(Future.successful(HttpResponse(INTERNAL_SERVER_ERROR)))

        val result = TestLookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader))

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsJson(result) shouldBe expectedJsonResult
      }

      "when residency status is not returned from the response handler service" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any())).thenReturn(successfulRetrieval)

        val nino = Nino("LE241131B")
        val uuid: String = "76648d82-309e-484d-a310-d0ffd2997794"

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INTERNAL_SERVER_ERROR",
            |  "message": "Internal server error"
            |}
          """.stripMargin)

        when(mockCachingConnector.getCachedData(any())(any())).thenReturn(Future.successful(HttpResponse(OK, Some(Json.toJson(expectedNino)))))
        when(mockHttpResponseHandlerService.handleResidencyStatusResponse(any(), any())(any(), any())).thenReturn(Future.successful(Right("")))

        val result = TestLookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader))

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsJson(result) shouldBe expectedJsonResult
      }
    }
  }

}

