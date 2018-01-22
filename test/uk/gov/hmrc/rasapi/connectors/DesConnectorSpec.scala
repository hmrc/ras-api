/*
 * Copyright 2018 HM Revenue & Customs
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
import org.mockito.Matchers._
import org.mockito.Mockito.{reset, when}
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers._
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.rasapi.models._

import scala.concurrent.Future

class DesConnectorSpec extends UnitSpec with OneAppPerSuite with BeforeAndAfter with MockitoSugar {

  implicit val hc = HeaderCarrier()

  before{
    reset(mockHttpGet)
  }

  val mockHttpGet = mock[HttpGet]
  val mockHttpPost = mock[HttpPost]
  implicit val format = ResidencyStatusFormats.successFormats
  implicit val formatF = ResidencyStatusFormats.failureFormats

  object TestDesConnector extends DesConnector {
    override val httpGet: HttpGet = mockHttpGet
    override val httpPost: HttpPost = mockHttpPost
    override val desBaseUrl = ""
    override def getResidencyStatusUrl(nino: String) = ""
    override val edhUrl: String = "test-url"
  }

  val residencyStatus = Json.parse(
    """{
         "currentYearResidencyStatus" : "scotResident",
         "nextYearForecastResidencyStatus" : "scotResident"
        }
    """.stripMargin
  )

  "DESConnector getResidencyStatus" should {

    "handle successful response when 200 is returned from des" in {

      when(mockHttpGet.GET[HttpResponse](any())(any(),any(), any())).
        thenReturn(Future.successful(HttpResponse(200, Some(residencyStatus))))

      val result = await(TestDesConnector.getResidencyStatus(Nino("LE241131B")))
      result.status shouldBe OK
    }

    "handle 404 error returned from des" in {

      when(mockHttpGet.GET[HttpResponse](any())(any(),any(), any())).
        thenReturn(Future.failed(new uk.gov.hmrc.http.NotFoundException("")))

      intercept[uk.gov.hmrc.http.NotFoundException] {
        await(TestDesConnector.getResidencyStatus(Nino("LE241131B")))
      }
    }

    "handle 500 error returned from des" in {

      when(mockHttpGet.GET[HttpResponse](any())(any(),any(), any())).
        thenReturn(Future.successful(HttpResponse(500)))

      val result = TestDesConnector.getResidencyStatus(Nino("LE241131B"))
      await(result).status shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "DESConnector sendDataToEDH" should {

    "handle successful response when 200 is returned from EDH" in {

      when(mockHttpPost.POST[EDHAudit, HttpResponse](any(), any(), any())(any(), any(),any(), any())).
        thenReturn(Future.successful(HttpResponse(200)))

      val userId = "123456"
      val nino = "LE241131B"
      val resStatus = ResidencyStatus(currentYearResidencyStatus = "scotResident",
                                      nextYearForecastResidencyStatus = "scotResident")

      val result = await(TestDesConnector.sendDataToEDH(userId, nino, resStatus))
      result.status shouldBe OK
    }

    "handle successful response when 500 is returned from EDH" in {
      when(mockHttpPost.POST[EDHAudit, HttpResponse](any(), any(), any())(any(), any(),any(), any())).
        thenReturn(Future.successful(HttpResponse(500)))

      val userId = "123456"
      val nino = "LE241131B"
      val resStatus = ResidencyStatus(currentYearResidencyStatus = "scotResident",
                                      nextYearForecastResidencyStatus = "scotResident")

      val result = await(TestDesConnector.sendDataToEDH(userId, nino, resStatus))
      result.status shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "DESConnector getResidencyStatus with matching" should {

    "handle successful response when 200 is returned from des" in {

      val successresponse = ResidencyStatusSuccess(nino="AB123456C",deathDate = Some(""),deathDateStatus = Some(""),deseasedIndicator = false,
        currentYearResidencyStatus = "Uk",nextYearResidencyStatus = "Scottish")
      when(mockHttpPost.POST[IndividualDetails,HttpResponse](any(), any(), any())(any(), any(),any(), any())).
        thenReturn(Future.successful(HttpResponse(200, Some(Json.toJson(successresponse)))))

      val result = await(TestDesConnector.getResidencyStatus(IndividualDetails("AB123456C","JOHN", "SMITH", new DateTime("1990-02-21"))))
      result.isLeft shouldBe true
      result.left.get shouldBe ResidencyStatus("otherUKResident","scotResident")
    }

    "handle failure response from des" in {
      val errorResponse = ResidencyStatusFailure("NOT_MATCHED","matching failed")
      when(mockHttpPost.POST[IndividualDetails,HttpResponse](any(), any(), any())(any(), any(),any(), any())).
        thenReturn(Future.successful(HttpResponse(403, Some(Json.toJson(errorResponse)))))

      val result = await(TestDesConnector.getResidencyStatus(IndividualDetails("AB123456C","JOHN", "Lewis", new DateTime("1990-02-21"))))
      result.isLeft shouldBe false
      result.right.get shouldBe errorResponse
    }

    "handle unexpected responses as 500 from des" in {

      when(mockHttpPost.POST[IndividualDetails,HttpResponse](any(), any(), any())(any(), any(),any(), any())).
        thenReturn(Future.successful(HttpResponse(500)))
      val errorResponse = ResidencyStatusFailure("INTERNAL_SERVER_ERROR","Internal server error")

      val result = await(TestDesConnector.getResidencyStatus(IndividualDetails("AB123456C","JOHN", "Lewis", new DateTime("1990-02-21"))))
      result.isLeft shouldBe false
      result.right.get shouldBe errorResponse
    }
    "handle unexpected responses as 400 from des" in {

      when(mockHttpPost.POST[IndividualDetails,HttpResponse](any(), any(), any())(any(), any(),any(), any())).
        thenReturn(Future.successful(HttpResponse(404,Some(Json.toJson("MATCHING_FAILED")))))
      val errorResponse = ResidencyStatusFailure("MATCHING_FAILED","The customer details provided did not match with HMRC’s records.")

      val result = await(TestDesConnector.getResidencyStatus(IndividualDetails("AB15456C","Dawn", "Lewis", new DateTime("1990-02-21"))))
      result.isLeft shouldBe false
      result.right.get shouldBe errorResponse
    }
  }

}

