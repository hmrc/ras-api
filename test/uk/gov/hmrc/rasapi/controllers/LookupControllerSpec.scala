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

package uk.gov.hmrc.rasapi.controllers

import org.joda.time.DateTime
import org.mockito.ArgumentMatchers
import org.mockito.ArgumentMatchers.{eq => Meq, _}
import org.mockito.Mockito._
import org.scalatest.BeforeAndAfter
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.functional.syntax.{unlift, _}
import play.api.libs.json.{JsPath, Json, Writes}
import play.api.mvc.ControllerComponents
import play.api.test.Helpers._
import play.api.test.{FakeRequest, Helpers}
import play.mvc.Http.HeaderNames
import uk.gov.hmrc.api.controllers.ErrorAcceptHeaderInvalid
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.rasapi.config.{AppContext, RasAuthConnector}
import uk.gov.hmrc.rasapi.connectors.DesConnector
import uk.gov.hmrc.rasapi.helpers.ResidencyYearResolver
import uk.gov.hmrc.rasapi.metrics.Metrics
import uk.gov.hmrc.rasapi.models._
import uk.gov.hmrc.rasapi.services.AuditService
import uk.gov.hmrc.rasapi.utils.ErrorConverter

import scala.concurrent.{ExecutionContext, Future}

class LookupControllerSpec extends UnitSpec with MockitoSugar with GuiceOneAppPerSuite with BeforeAndAfter {

  override implicit lazy val app = new GuiceApplicationBuilder()
    .configure("api-v2_0.enabled" -> "true")
    .build()

  implicit val hc = HeaderCarrier()

  val acceptHeader: (String, String) = (HeaderNames.ACCEPT, "application/vnd.hmrc.2.0+json")
  val acceptHeaderV1: (String, String) = (HeaderNames.ACCEPT, "application/vnd.hmrc.1.0+json")

  val mockDesConnector = mock[DesConnector]
  val mockAuditService = mock[AuditService]
  val mockAuthConnector = mock[RasAuthConnector]
  val mockResidencyYearResolver = mock[ResidencyYearResolver]
  val mockMetrics = app.injector.instanceOf[Metrics]
  val appContext = app.injector.instanceOf[AppContext]
  val errorConverer = app.injector.instanceOf[ErrorConverter]
  val mockCC = mock[ControllerComponents]

  val expectedNino = uk.gov.hmrc.rasapi.models.Nino("LE241131B")

  private val enrolmentIdentifier1 = EnrolmentIdentifier("PSAID", "A123456")
  private val enrolment1 = new Enrolment(key = "HMRC-PSA-ORG", identifiers = List(enrolmentIdentifier1), state = "Activated", None)
  private val enrolmentIdentifier2 = EnrolmentIdentifier("PPID", "A123456")
  private val enrolment2 = new Enrolment(key = "HMRC-PP-ORG", identifiers = List(enrolmentIdentifier2), state = "Activated", None)
  private val enrolments = new Enrolments(Set(enrolment1, enrolment2))

  val successfulRetrieval: Future[Enrolments] = Future.successful(enrolments)

  val individualDetails = IndividualDetails("LE241131B", "Joe", "Bloggs", new DateTime("1990-12-03"))

  val STATUS_DECEASED: String = "DECEASED"
  val STATUS_MATCHING_FAILED: String = "STATUS_UNAVAILABLE"
  val STATUS_INTERNAL_SERVER_ERROR: String = "INTERNAL_SERVER_ERROR"
  val STATUS_TOO_MANY_REQUESTS: String = "TOO_MANY_REQUESTS"
  val STATUS_SERVICE_UNAVAILABLE: String = "SERVICE_UNAVAILABLE"

  implicit val individualDetailssWrites: Writes[IndividualDetails] = (
    (JsPath \ "nino").write[String] and
      (JsPath \ "firstName").write[String] and
      (JsPath \ "lastName").write[String] and
      (JsPath \ "dateOfBirth").write[String].contramap[DateTime](date => date.toString("yyyy-MM-dd"))
    ) (unlift(IndividualDetails.unapply))

  object TestLookupController extends LookupController(
    mockDesConnector,
    mockMetrics,
    mockAuditService,
    mockAuthConnector,
    mockResidencyYearResolver,
    appContext,
    errorConverer,
    mockCC,
    ExecutionContext.global
  ) {

    override def getCurrentDate: DateTime = new DateTime(2018, 7, 6, 0, 0, 0, 0)

    override lazy val allowDefaultRUK: Boolean = false
    override lazy val STATUS_DECEASED: String = "DECEASED"
    override lazy val STATUS_MATCHING_FAILED: String = "STATUS_UNAVAILABLE"
    override lazy val STATUS_TOO_MANY_REQUESTS: String = "TOO_MANY_REQUESTS"
    override lazy val STATUS_SERVICE_UNAVAILABLE: String = "SERVICE_UNAVAILABLE"
    override lazy val apiV2_0Enabled: Boolean = true
  }

  object TestLookupControllerFeb18 extends LookupController(
    mockDesConnector,
    mockMetrics,
    mockAuditService,
    mockAuthConnector,
    mockResidencyYearResolver,
    appContext,
    errorConverer,
    mockCC,
    ExecutionContext.global
  ) {
    override def getCurrentDate: DateTime = new DateTime(2018, 2, 15, 0, 0, 0, 0)

    override lazy val allowDefaultRUK: Boolean = true
    override lazy val STATUS_DECEASED: String = "DECEASED"
    override lazy val STATUS_MATCHING_FAILED: String = "STATUS_UNAVAILABLE"
    override lazy val STATUS_TOO_MANY_REQUESTS: String = "TOO_MANY_REQUESTS"
    override lazy val STATUS_SERVICE_UNAVAILABLE: String = "SERVICE_UNAVAILABLE"
    override lazy val apiV2_0Enabled: Boolean = true
  }

  object TestLookupControllerFeb19 extends LookupController(
    mockDesConnector,
    mockMetrics,
    mockAuditService,
    mockAuthConnector,
    mockResidencyYearResolver,
    appContext,
    errorConverer,
    mockCC,
    ExecutionContext.global
  ) {
    override def getCurrentDate: DateTime = new DateTime(2019, 2, 15, 0, 0, 0, 0)

    override lazy val allowDefaultRUK: Boolean = false
    override lazy val STATUS_DECEASED: String = "DECEASED"
    override lazy val STATUS_MATCHING_FAILED: String = "STATUS_UNAVAILABLE"
    override lazy val STATUS_SERVICE_UNAVAILABLE: String = "SERVICE_UNAVAILABLE"
    override lazy val STATUS_TOO_MANY_REQUESTS: String = "TOO_MANY_REQUESTS"
    override lazy val apiV2_0Enabled: Boolean = true
  }

  object TestLookupControllerVersion1 extends LookupController(
    mockDesConnector,
    mockMetrics,
    mockAuditService,
    mockAuthConnector,
    mockResidencyYearResolver,
    appContext,
    errorConverer,
    mockCC,
    ExecutionContext.global
  ) {
    override def getCurrentDate: DateTime = new DateTime(2019, 1, 1, 0, 0, 0, 0)

    override lazy val allowDefaultRUK: Boolean = false
    override lazy val STATUS_DECEASED: String = "DECEASED"
    override lazy val STATUS_MATCHING_FAILED: String = "STATUS_UNAVAILABLE"
    override lazy val STATUS_TOO_MANY_REQUESTS: String = "TOO_MANY_REQUESTS"
    override lazy val STATUS_SERVICE_UNAVAILABLE: String = "SERVICE_UNAVAILABLE"
    override lazy val apiV2_0Enabled: Boolean = false
  }

  before {
    reset(mockAuditService)
    reset(mockDesConnector)
    when(mockDesConnector.otherUk).thenReturn("otherUKResident")
    when(mockDesConnector.scotRes).thenReturn("scotResident")
  }

  "The lookup controller endpoint" should {

    "audit a successful lookup response" when {
      "a valid request has been submitted and the date is between january and april 2018" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

        when(mockResidencyYearResolver.isBetweenJanAndApril()).thenReturn(true)

        val residencyStatus = ResidencyStatus("scotResident", Some("otherUKResident"))

        when(mockDesConnector.getResidencyStatus(any(), any(), any(), any())).thenReturn(Future.successful(Left(residencyStatus)))

        await(TestLookupControllerFeb18.getResidencyStatus()
          .apply(FakeRequest(Helpers.GET, s"/residency-status")
            .withHeaders(acceptHeader)
            .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites))))

        verify(mockAuditService).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/residency-status"),
          auditData = Meq(Map("successfulLookup" -> "true",
            "CYStatus" -> "otherUKResident",
            "NextCYStatus" -> "otherUKResident",
            "nino" -> "LE241131B",
            "userIdentifier" -> "A123456",
            "requestSource" -> "API"))
        )(any())
      }

      "a valid request has been submitted and the date is between january and april 2019" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

        when(mockResidencyYearResolver.isBetweenJanAndApril()).thenReturn(true)

        val residencyStatus = ResidencyStatus("scotResident", Some("otherUKResident"))

        when(mockDesConnector.getResidencyStatus(any(), any(), any(), any())).thenReturn(Future.successful(Left(residencyStatus)))

        await(TestLookupControllerFeb19.getResidencyStatus()
          .apply(FakeRequest(Helpers.GET, s"/residency-status")
            .withHeaders(acceptHeader)
            .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites))))

        verify(mockAuditService).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/residency-status"),
          auditData = Meq(Map("successfulLookup" -> "true",
            "CYStatus" -> "scotResident",
            "NextCYStatus" -> "otherUKResident",
            "nino" -> "LE241131B",
            "userIdentifier" -> "A123456",
            "requestSource" -> "API"))
        )(any())
      }

      "a valid request has been submitted and the date is between april and december" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

        when(mockResidencyYearResolver.isBetweenJanAndApril()).thenReturn(false)

        val residencyStatus = ResidencyStatus("otherUKResident", Some("otherUKResident"))

        when(mockDesConnector.getResidencyStatus(any(), any(), any(), any())).thenReturn(Future.successful(Left(residencyStatus)))

        await(TestLookupController.getResidencyStatus()
          .apply(FakeRequest(Helpers.GET, s"/residency-status")
            .withHeaders(acceptHeader)
            .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites))))

        verify(mockAuditService).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/residency-status"),
          auditData = Meq(Map("successfulLookup" -> "true",
            "CYStatus" -> "otherUKResident",
            "nino" -> "LE241131B",
            "userIdentifier" -> "A123456",
            "requestSource" -> "API"))
        )(any())
      }

      "a valid request has been submitted and the date is between april and december and the individual is deceased" in {
        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

        when(mockResidencyYearResolver.isBetweenJanAndApril()).thenReturn(false)

        when(mockDesConnector.getResidencyStatus(any(), any(), any(), any())).thenReturn(Future.successful(Right(ResidencyStatusFailure(STATUS_DECEASED, "Individual is deceased"))))

        await(TestLookupController.getResidencyStatus()
          .apply(FakeRequest(Helpers.GET, s"/residency-status")
            .withHeaders(acceptHeader)
            .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites))))

        verify(mockAuditService).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/residency-status"),
          auditData = Meq(Map("successfulLookup" -> "false",
            "reason" -> STATUS_DECEASED,
            "nino" -> "LE241131B",
            "userIdentifier" -> "A123456",
            "requestSource" -> "API"))
        )(any())
      }
    }

    "audit a unsuccessful lookup response" when {

      "a no match is returned" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

        val residencyStatusFailure = ResidencyStatusFailure("STATUS_UNAVAILABLE", "")

        when(mockDesConnector.getResidencyStatus(any(), any(), any(), any())).thenReturn(Future.successful(Right(residencyStatusFailure)))

        await(TestLookupController.getResidencyStatus()
          .apply(FakeRequest(Helpers.GET, s"/residency-status")
            .withHeaders(acceptHeader)
            .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites))))

        verify(mockAuditService).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/residency-status"),
          auditData = Meq(Map("nino" -> "LE241131B",
            "successfulLookup" -> "false",
            "reason" -> "MATCHING_FAILED",
            "userIdentifier" -> "A123456",
            "requestSource" -> "API"))
        )(any())
      }

      "a problem has occurred in the Head of Duty (HoD) system" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

        val residencyStatusFailure = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "")

        when(mockDesConnector.getResidencyStatus(any(), any(), any(), any())).thenReturn(Future.successful(Right(residencyStatusFailure)))

        await(TestLookupController.getResidencyStatus()
          .apply(FakeRequest(Helpers.GET, s"/residency-status")
            .withHeaders(acceptHeader)
            .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites))))

        verify(mockAuditService).audit(
          auditType = Meq("ReliefAtSourceResidency"),
          path = Meq(s"/residency-status"),
          auditData = Meq(Map("nino" -> "LE241131B",
            "successfulLookup" -> "false",
            "reason" -> s"$STATUS_INTERNAL_SERVER_ERROR",
            "userIdentifier" -> "A123456",
            "requestSource" -> "API"))
        )(any())
      }
    }
  }

  "LookupController" when {

    "Version 2 is disabled" when {
      "a version 1.0 request payload is given" should {
        "return status 200 with correct residency status json" in {

          when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

          when(mockResidencyYearResolver.isBetweenJanAndApril()).thenReturn(true)

          val residencyStatus = ResidencyStatus("scotResident", Some("otherUKResident"))

          val expectedJsonResult = Json.parse(
            """
              {
                "currentYearResidencyStatus" : "scotResident",
                "nextYearForecastResidencyStatus" : "otherUKResident"
              }
            """.stripMargin)

          when(mockDesConnector.getResidencyStatus(any(), any(), ArgumentMatchers.eq(V1_0), any())).thenReturn(Future.successful(Left(residencyStatus)))

          val result = TestLookupControllerVersion1.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeaderV1)
            .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites)))

          status(result) shouldBe OK
          contentAsJson(result) shouldBe expectedJsonResult
        }
      }

      "a version 2.0 request payload is given" should {
        "return status 406 with invalid accept header json" in {
          val result = TestLookupControllerVersion1.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader)
            .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites)))

          status(result) shouldBe NOT_ACCEPTABLE
          contentAsJson(result) shouldBe Json.toJson(ErrorAcceptHeaderInvalid)
        }
      }

    }

    "Version 2 is enabled" should {
      "return status 200 with correct residency status json" when {
        "a valid request payload is given and the date of the request is between january and april 2018" in {

          when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

          when(mockResidencyYearResolver.isBetweenJanAndApril()).thenReturn(true)

          val residencyStatus = ResidencyStatus("scotResident", Some("otherUKResident"))

          val expectedJsonResult = Json.parse(
            """
            {
              "currentYearResidencyStatus" : "otherUKResident",
              "nextYearForecastResidencyStatus" : "otherUKResident"
            }
          """.stripMargin)

          when(mockDesConnector.getResidencyStatus(any(), any(), any(), any())).thenReturn(Future.successful(Left(residencyStatus)))

          val result = TestLookupControllerFeb18.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader)
            .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites)))

          status(result) shouldBe OK
          contentAsJson(result) shouldBe expectedJsonResult
        }

        "a version 1.0 request payload is given and the date of the request is between january and april 2018" in {

          when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

          when(mockResidencyYearResolver.isBetweenJanAndApril()).thenReturn(true)

          val residencyStatus = ResidencyStatus("scotResident", Some("otherUKResident"))

          val expectedJsonResult = Json.parse(
            """
            {
              "currentYearResidencyStatus" : "otherUKResident",
              "nextYearForecastResidencyStatus" : "otherUKResident"
            }
          """.stripMargin)

          when(mockDesConnector.getResidencyStatus(any(), any(), ArgumentMatchers.eq(V1_0), any())).thenReturn(Future.successful(Left(residencyStatus)))

          val result = TestLookupControllerFeb18.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeaderV1)
            .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites)))

          status(result) shouldBe OK
          contentAsJson(result) shouldBe expectedJsonResult
        }

        "a version 2.0 request payload is given and the date of the request is between january and april 2018" in {
          when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

          when(mockResidencyYearResolver.isBetweenJanAndApril()).thenReturn(true)

          val residencyStatus = ResidencyStatus("scotResident", Some("otherUKResident"))

          val expectedJsonResult = Json.parse(
            """
            {
              "currentYearResidencyStatus" : "otherUKResident",
              "nextYearForecastResidencyStatus" : "otherUKResident"
            }
          """.stripMargin)

          when(mockDesConnector.getResidencyStatus(any(), any(), ArgumentMatchers.eq(V2_0), any())).thenReturn(Future.successful(Left(residencyStatus)))

          val result = TestLookupControllerFeb18.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader)
            .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites)))

          status(result) shouldBe OK
          contentAsJson(result) shouldBe expectedJsonResult
        }

        "a valid request payload is given and the date of the request is between january and april 2019" in {

          when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

          when(mockResidencyYearResolver.isBetweenJanAndApril()).thenReturn(true)

          val residencyStatus = ResidencyStatus("scotResident", Some("otherUKResident"))

          val expectedJsonResult = Json.parse(
            """
            {
              "currentYearResidencyStatus" : "scotResident",
              "nextYearForecastResidencyStatus" : "otherUKResident"
            }
          """.stripMargin)

          when(mockDesConnector.getResidencyStatus(any(), any(), any(), any())).thenReturn(Future.successful(Left(residencyStatus)))

          val result = TestLookupControllerFeb19.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader)
            .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites)))

          status(result) shouldBe OK
          contentAsJson(result) shouldBe expectedJsonResult
        }

        "a valid request payload is given with a nino which is 9 characters in length e.g. AA123456A" in {

          when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

          when(mockResidencyYearResolver.isBetweenJanAndApril()).thenReturn(true)

          val residencyStatus = ResidencyStatus("otherUKResident", Some("otherUKResident"))

          val expectedJsonResult = Json.parse(
            """
            {
              "currentYearResidencyStatus" : "otherUKResident",
              "nextYearForecastResidencyStatus" : "otherUKResident"
            }
          """.stripMargin)

          when(mockDesConnector.getResidencyStatus(any(), any(), any(), any())).thenReturn(Future.successful(Left(residencyStatus)))

          val result = TestLookupController.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader)
            .withJsonBody(Json.toJson(individualDetails.copy(nino = individualDetails.nino.toLowerCase))))

          status(result) shouldBe OK
          contentAsJson(result) shouldBe expectedJsonResult
        }

        "a valid request payload is given and the date of the request is between april and december" in {

          when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

          when(mockResidencyYearResolver.isBetweenJanAndApril()).thenReturn(false)

          val residencyStatus = ResidencyStatus("otherUKResident", Some("otherUKResident"))

          val expectedJsonResult = Json.parse(
            """
            {
              "currentYearResidencyStatus" : "otherUKResident"
            }
          """.stripMargin)

          when(mockDesConnector.getResidencyStatus(any(), any(), any(), any())).thenReturn(Future.successful(Left(residencyStatus)))

          val result = TestLookupController.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader)
            .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites)))

          status(result) shouldBe OK
          contentAsJson(result) shouldBe expectedJsonResult
        }

        "a valid request payload is given with a nino which is 8 characters in length e.g. AA123456" in {

          when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
          when(mockResidencyYearResolver.isBetweenJanAndApril()).thenReturn(true)

          val residencyStatus = ResidencyStatus("otherUKResident", Some("otherUKResident"))

          val expectedJsonResult = Json.parse(
            """
            {
              "currentYearResidencyStatus" : "otherUKResident",
              "nextYearForecastResidencyStatus" : "otherUKResident"
            }
          """.stripMargin)

          when(mockDesConnector.getResidencyStatus(any(), any(), any(), any())).thenReturn(Future.successful(Left(residencyStatus)))

          val result = TestLookupController.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader)
            .withJsonBody(Json.toJson(individualDetails.copy(nino = "AA123456"))))

          status(result) shouldBe OK
          contentAsJson(result) shouldBe expectedJsonResult
        }
      }

      "return status 400" when {
        "an invalid payload is provided" in {

          when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

          val invalidPayload = Json.parse(
            """
              |{
              |   "firstName": "Joe",
              |   "nino": "AA123243E",
              |   "dob": ""
              |}
            """.stripMargin
          )

          val expectedJsonResult = Json.parse(
            """
              |{
              |  "code": "BAD_REQUEST",
              |  "message": "Bad Request",
              |  "errors": [
              |     {
              |       "code": "INVALID_FORMAT",
              |       "message": "Invalid format has been used",
              |       "path": "/nino"
              |     },
              |     {
              |       "code": "MISSING_FIELD",
              |       "message": "This field is required",
              |       "path": "/lastName"
              |     },
              |     {
              |       "code": "MISSING_FIELD",
              |       "message": "This field is required",
              |       "path": "/dateOfBirth"
              |     }
              |  ]
              |}
            """.stripMargin)

          val result = TestLookupController.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/")
            .withHeaders(acceptHeader)
            .withJsonBody(invalidPayload))

          status(result) shouldBe BAD_REQUEST
          contentAsJson(result) shouldBe expectedJsonResult
        }
      }
    }

    "return status 401 (Unauthorised)" when {
      "a valid lookup request has been submitted with no PSA or PP enrolments" in {

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(Future.failed(new InsufficientEnrolments))

        val authorisationHeader: (String, String) = (HeaderNames.AUTHORIZATION, "Bearer ABC")

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "UNAUTHORIZED",
            |  "message": "Supplied OAuth token not authorised to access data for given tax identifier(s)"
            |}
          """.stripMargin)

        val result = TestLookupController.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/").
          withHeaders(acceptHeader, authorisationHeader))

        status(result) shouldBe UNAUTHORIZED
        contentAsJson(result) shouldBe expectedJsonResult
      }

      "a valid lookup request has been submitted with no authorization header present" in {

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(Future.failed(new MissingBearerToken))

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INVALID_CREDENTIALS",
            |  "message": "Invalid OAuth token supplied for user-restricted or application-restricted resource (including expired token)"
            |}
          """.stripMargin)

        val result = TestLookupController.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/")
          .withHeaders(acceptHeader)
          .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites)))

        status(result) shouldBe UNAUTHORIZED
        contentAsJson(result) shouldBe expectedJsonResult
      }

      "a valid lookup request has been submitted with no value declared in the authorization header" in {

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(Future.failed(new InvalidBearerToken))

        val authorisationHeader: (String, String) = (HeaderNames.AUTHORIZATION, "")

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INVALID_CREDENTIALS",
            |  "message": "Invalid OAuth token supplied for user-restricted or application-restricted resource (including expired token)"
            |}
          """.stripMargin)

        val result = TestLookupController.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/")
          .withHeaders(acceptHeader, authorisationHeader)
          .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites)))

        status(result) shouldBe UNAUTHORIZED
        contentAsJson(result) shouldBe expectedJsonResult
      }

      "a valid lookup request has been submitted with an expired bearer token in the authorization header" in {
        // The bearer token used in this test is not valid but for purposes of testing is being treated as a valid bearer token.

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(Future.failed(new BearerTokenExpired))

        val authorisationHeader: (String, String) = (HeaderNames.AUTHORIZATION, "")

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INVALID_CREDENTIALS",
            |  "message": "Invalid OAuth token supplied for user-restricted or application-restricted resource (including expired token)"
            |}
          """.stripMargin)

        val result = TestLookupController.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/")
          .withHeaders(acceptHeader, authorisationHeader)
          .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites)))

        status(result) shouldBe UNAUTHORIZED
        contentAsJson(result) shouldBe expectedJsonResult
      }

      "a valid match request has been submitted with an invalid bearer token in the authorization header" in {

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(Future.failed(new SessionRecordNotFound))

        val authorisationHeader: (String, String) = (HeaderNames.AUTHORIZATION, "Bearer ABC")

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INVALID_CREDENTIALS",
            |  "message": "Invalid OAuth token supplied for user-restricted or application-restricted resource (including expired token)"
            |}
          """.stripMargin)

        val result = TestLookupController.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/")
          .withHeaders(acceptHeader, authorisationHeader)
          .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites)))

        status(result) shouldBe UNAUTHORIZED
        contentAsJson(result) shouldBe expectedJsonResult
      }
    }

    "return status 403" when {
      "MATCHING_FAILED is returned from the connector" in {
        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

        val expectedJsonResult = Json.parse(
          s"""
             |{
             |  "code": "$STATUS_MATCHING_FAILED",
             |  "message": "Cannot provide a residency status for this pension scheme member."
             |}
          """.stripMargin)

        val residencyStatusFailure = ResidencyStatusFailure("STATUS_UNAVAILABLE", "")

        when(mockDesConnector.getResidencyStatus(any(), any(), any(), any())).thenReturn(Future.successful(Right(residencyStatusFailure)))

        val result = TestLookupController.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/")
          .withHeaders(acceptHeader)
          .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites)))

        status(result) shouldBe FORBIDDEN
        contentAsJson(result) shouldBe expectedJsonResult
      }
    }

    "return status 429" when {

      "when residency status is not returned from the response handler service" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

        val residencyStatusFailure = ResidencyStatusFailure("TOO_MANY_REQUESTS", "")

        when(mockDesConnector.getResidencyStatus(any(), any(), any(), any())).thenReturn(Future.successful(Right(residencyStatusFailure)))

        val result = TestLookupController.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/")
          .withHeaders(acceptHeader)
          .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites)))

        status(result) shouldBe TOO_MANY_REQUESTS
      }
    }

    "return status 500" when {

      "when residency status is not returned from the response handler service" in {

        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

        val expectedJsonResult = Json.parse(
          s"""
             |{
             |  "code": "$STATUS_INTERNAL_SERVER_ERROR",
             |  "message": "Internal server error"
             |}
          """.stripMargin)

        val residencyStatusFailure = ResidencyStatusFailure("INTERNAL_SERVER_ERROR", "")

        when(mockDesConnector.getResidencyStatus(any(), any(), any(), any())).thenReturn(Future.successful(Right(residencyStatusFailure)))

        val result = TestLookupController.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/")
          .withHeaders(acceptHeader)
          .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites)))

        status(result) shouldBe INTERNAL_SERVER_ERROR
        contentAsJson(result) shouldBe expectedJsonResult
      }
    }

    "return status 503" when {
      "DES returns a service unavailable response" in {
        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

        val expectedJsonResult = Json.parse(
          s"""
             |{
             |  "code": "SERVER_ERROR",
             |  "message": "Service unavailable"
             |}
          """.stripMargin)

        val residencyStatusFailure = ResidencyStatusFailure("SERVICE_UNAVAILABLE", "")

        when(mockDesConnector.getResidencyStatus(any(), any(), any(), any())).thenReturn(Future.successful(Right(residencyStatusFailure)))

        val result = TestLookupController.getResidencyStatus().apply(FakeRequest(Helpers.GET, "/")
          .withHeaders(acceptHeader)
          .withJsonBody(Json.toJson(individualDetails)(individualDetailssWrites)))

        status(result) shouldBe SERVICE_UNAVAILABLE
        contentAsJson(result) shouldBe expectedJsonResult
      }
    }
  }

}

