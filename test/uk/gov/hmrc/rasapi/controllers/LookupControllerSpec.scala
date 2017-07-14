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
import uk.gov.hmrc.rasapi.connectors.{CachingConnector, DesConnector}
import org.mockito.Matchers
import org.mockito.Matchers.{eq => Meq, _}
import org.mockito.Mockito._
import org.scalatest.{ShouldMatchers, WordSpec}
import uk.gov.hmrc.play.http.HeaderCarrier
import uk.gov.hmrc.rasapi.models._
import uk.gov.hmrc.rasapi.services.AuditService

import scala.concurrent.Future

class LookupControllerSpec extends WordSpec with MockitoSugar with ShouldMatchers with OneAppPerSuite {

  implicit val hc = HeaderCarrier()

  val acceptHeader: (String, String) = (HeaderNames.ACCEPT, "application/vnd.hmrc.1.0+json")

  val mockDesConnector = mock[DesConnector]
  val mockCachingConnector = mock[CachingConnector]
  val mockAuditService = mock[AuditService]

  object TestLookupController extends LookupController {
    override val desConnector = mockDesConnector
    override val cachingConnector = mockCachingConnector
    override val auditService: AuditService = mockAuditService
  }

  "The lookup controller endpoint" should {

    "audit a successful lookup response" when {
      "a valid uuid has been submitted" in {

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"
        val customerCacheResponse = CustomerCacheResponse(200, Some(Nino("LE241131B")))
        val residencyStatus = Some(ResidencyStatus("otherUKResident", "otherUKResident"))
        val desResponse = SuccessfulDesResponse(residencyStatus)

        when(mockCachingConnector.getCachedData(Meq(uuid))(Matchers.any())).thenReturn(Future.successful(customerCacheResponse))
        when(mockDesConnector.getResidencyStatus(any())(Matchers.any())).thenReturn(Future.successful(desResponse))

        val result = await(TestLookupController.getResidencyStatus(uuid)
                        .apply(FakeRequest(Helpers.GET, s"/relief-at-source/customer/$uuid/residency-status")
                        .withHeaders(acceptHeader)))

        verify(mockAuditService).audit(
          auditType = Meq("residencyLookupSuccess"),
          path = Meq(s"/relief-at-source/customer/$uuid/residency-status"),
          auditData = Meq(Map("uuid" -> uuid,
                              "nino" -> "LE241131B",
                              "residencyStatusDefined" -> "true",
                              "CYResidencyStatus" -> "otherUKResident",
                              "CYPlus1ResidencyStatus" -> "otherUKResident"))
        )(any())
      }
    }

    "audit an unsuccessful lookup response" when {
      "an invalid uuid is given" in {

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"
        val customerCacheResponse = CustomerCacheResponse(403, None)

        when(mockCachingConnector.getCachedData(Meq(uuid))(Matchers.any())).thenReturn(Future.successful(customerCacheResponse))

        val result = await(TestLookupController.getResidencyStatus(uuid)
          .apply(FakeRequest(Helpers.GET, s"/relief-at-source/customer/$uuid/residency-status")
            .withHeaders(acceptHeader)))

        verify(mockAuditService).audit(
          auditType = Meq("residencyLookupFailure"),
          path = Meq(s"/relief-at-source/customer/$uuid/residency-status"),
          auditData = Meq(Map("uuid" -> uuid,
            "nino" -> "NO_NINO_DEFINED",
            "residencyStatusDefined" -> "false",
            "failureReason" -> "INVALID_UUID"))
        )(any())
      }

      "a problem occurred while trying to call caching service" in {

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"
        val customerCacheResponse = CustomerCacheResponse(500, None)

        when(mockCachingConnector.getCachedData(Meq(uuid))(Matchers.any())).thenReturn(Future.successful(customerCacheResponse))

        val result = await(TestLookupController.getResidencyStatus(uuid)
          .apply(FakeRequest(Helpers.GET, s"/relief-at-source/customer/$uuid/residency-status")
            .withHeaders(acceptHeader)))

        verify(mockAuditService).audit(
          auditType = Meq("residencyLookupFailure"),
          path = Meq(s"/relief-at-source/customer/$uuid/residency-status"),
          auditData = Meq(Map("uuid" -> uuid,
            "nino" -> "NO_NINO_DEFINED",
            "residencyStatusDefined" -> "false",
            "failureReason" -> "INTERNAL_SERVER_ERROR"))
        )(any())
      }

      "there is corrupted data held in the Head of Duty (HoD) system" in {

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"
        val customerCacheResponse = CustomerCacheResponse(200, Some(Nino("LE241131B")))
        val desResponse = AccountLockedResponse

        when(mockCachingConnector.getCachedData(Meq(uuid))(Matchers.any())).thenReturn(Future.successful(customerCacheResponse))
        when(mockDesConnector.getResidencyStatus(any())(Matchers.any())).thenReturn(Future.successful(desResponse))

        val result = await(TestLookupController.getResidencyStatus(uuid)
          .apply(FakeRequest(Helpers.GET, s"/relief-at-source/customer/$uuid/residency-status")
            .withHeaders(acceptHeader)))

        verify(mockAuditService).audit(
          auditType = Meq("residencyLookupFailure"),
          path = Meq(s"/relief-at-source/customer/$uuid/residency-status"),
          auditData = Meq(Map("uuid" -> uuid,
            "nino" -> "LE241131B",
            "residencyStatusDefined" -> "false",
            "failureReason" -> "INVALID_RESIDENCY_STATUS"))
        )(any())
      }

      "a problem occurred while trying to call the Head of Duty (HoD) system" in {

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"
        val customerCacheResponse = CustomerCacheResponse(200, Some(Nino("LE241131B")))
        val desResponse = InternalServerErrorResponse

        when(mockCachingConnector.getCachedData(Meq(uuid))(Matchers.any())).thenReturn(Future.successful(customerCacheResponse))
        when(mockDesConnector.getResidencyStatus(any())(Matchers.any())).thenReturn(Future.successful(desResponse))

        val result = await(TestLookupController.getResidencyStatus(uuid)
          .apply(FakeRequest(Helpers.GET, s"/relief-at-source/customer/$uuid/residency-status")
            .withHeaders(acceptHeader)))

        verify(mockAuditService).audit(
          auditType = Meq("residencyLookupFailure"),
          path = Meq(s"/relief-at-source/customer/$uuid/residency-status"),
          auditData = Meq(Map("uuid" -> uuid,
            "nino" -> "LE241131B",
            "residencyStatusDefined" -> "false",
            "failureReason" -> "INTERNAL_SERVER_ERROR"))
        )(any())
      }
    }
  }

  "LookupController" should {

    "return status 200 with correct residency status json" when {

      "a valid UUID is given" in {

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc2"
        val customerCacheResponse = CustomerCacheResponse(200, Some(Nino("LE241131B")))
        val residencyStatus = Some(ResidencyStatus("otherUKResident", "otherUKResident"))
        val desResponse = SuccessfulDesResponse(residencyStatus)

        val expectedJsonResult = Json.parse(
          """
            {
              "currentYearResidencyStatus" : "otherUKResident",
              "nextYearForecastResidencyStatus" : "otherUKResident"
            }
          """.stripMargin)

        when(mockCachingConnector.getCachedData(Meq(uuid))(Matchers.any())).thenReturn(Future.successful(customerCacheResponse))
        when(mockDesConnector.getResidencyStatus(Meq(Nino("LE241131B")))(Matchers.any())).thenReturn(Future.successful(desResponse))

        val result = TestLookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader))

        status(result) shouldBe 200
        contentAsJson(result) shouldBe expectedJsonResult
      }
    }

    "return status 403" when {

      "an invalid UUID is given" in {

        val uuid: String = "2800a7ab-fe20-42ca-98d7-c33f4133cfc1"
        val nino = Nino("LE241131B")
        val customerCacheResponse = CustomerCacheResponse(403, None)

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INVALID_UUID",
            |  "message": "The match has timed out and the UUID is no longer valid. The match (POST to /match) will need to be repeated."
            |}
          """.stripMargin)

        when(mockCachingConnector.getCachedData(Meq(uuid))(Matchers.any())).thenReturn(Future.successful(customerCacheResponse))

        val result = TestLookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader))

        status(result) shouldBe 403
        contentAsJson(result) shouldBe expectedJsonResult
      }
    }

    "return status 500" when {
      "an invalid UUID is given" in {

        val uuid: String = "76648d82-309e-484d-a310-d0ffd2997795"
        val customerCacheResponse = CustomerCacheResponse(500, None)

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INTERNAL_SERVER_ERROR",
            |  "message": "Internal server error"
            |}
          """.stripMargin)

        when(mockCachingConnector.getCachedData(Meq(uuid))(Matchers.any())).thenReturn(Future.successful(customerCacheResponse))

        val result = TestLookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader))

        status(result) shouldBe 500
        contentAsJson(result) shouldBe expectedJsonResult
      }
    }

    "return 403" when {
      "the account is locked" in {
        val nino = Nino("LE241131B")
        val customerCacheResponse = CustomerCacheResponse(200, Some(nino))
        val uuid: String = "76648d82-309e-484d-a310-d0ffd2997794"

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INVALID_RESIDENCY_STATUS",
            |  "message": "There is a problem with this member's account. Ask them to call HMRC."
            |}
          """.stripMargin)

        when(mockCachingConnector.getCachedData(Meq(uuid))(Matchers.any())).thenReturn(Future.successful(customerCacheResponse))
        when(mockDesConnector.getResidencyStatus(Meq(nino))(Matchers.any())).thenReturn(Future.successful(AccountLockedResponse))

        val result = TestLookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader))

        status(result) shouldBe 403
        contentAsJson(result) shouldBe expectedJsonResult
      }
    }

    "return 500" when {
      "when 500 is returned from desconnector" in {
        val nino = Nino("LE241131B")
        val customerCacheResponse = CustomerCacheResponse(200, Some(nino))
        val uuid: String = "76648d82-309e-484d-a310-d0ffd2997794"

        val expectedJsonResult = Json.parse(
          """
            |{
            |  "code": "INTERNAL_SERVER_ERROR",
            |  "message": "Internal server error"
            |}
          """.stripMargin)

        when(mockCachingConnector.getCachedData(Meq(uuid))(Matchers.any())).thenReturn(Future.successful(customerCacheResponse))
        when(mockDesConnector.getResidencyStatus(Meq(nino))(Matchers.any())).thenReturn(Future.successful(InternalServerErrorResponse))

        val result = TestLookupController.getResidencyStatus(uuid).apply(FakeRequest(Helpers.GET, "/").withHeaders(acceptHeader))

        status(result) shouldBe 500
        contentAsJson(result) shouldBe expectedJsonResult
      }
    }
  }

}

