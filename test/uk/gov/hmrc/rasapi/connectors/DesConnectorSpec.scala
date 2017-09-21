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

package uk.gov.hmrc.rasapi.connectors

import org.mockito.Matchers
import org.mockito.Mockito.when
import org.scalatest.mock.MockitoSugar
import org.scalatest.{ShouldMatchers, WordSpec}
import org.scalatestplus.play.OneAppPerSuite
import play.api.libs.json.Json
import play.api.test.Helpers.{await, _}
import uk.gov.hmrc.play.http._
import uk.gov.hmrc.rasapi.models._

import scala.concurrent.Future

class DesConnectorSpec extends WordSpec with OneAppPerSuite with MockitoSugar with ShouldMatchers{

  implicit val hc = HeaderCarrier()

  val mockHttp = mock[HttpGet]

  object TestDesConnector extends DesConnector {
    override val http: HttpGet = mockHttp
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

      when(mockHttp.GET[HttpResponse](Matchers.any())(Matchers.any(),Matchers.any())).
        thenReturn(Future.successful(HttpResponse(200, Some(residencyStatus))))

      val result = await(TestDesConnector.getResidencyStatus(Nino("LE241131B")))
      result.status shouldBe OK
    }

    "handle 404 error returned from des" in {

      when(mockHttp.GET[HttpResponse](Matchers.any())(Matchers.any(),Matchers.any())).
        thenReturn(Future.failed(new NotFoundException("")))

      intercept[NotFoundException] {
        await(TestDesConnector.getResidencyStatus(Nino("LE241131B")))
      }
    }

    "handle 500 error returned from des" in {

      when(mockHttp.GET[HttpResponse](Matchers.any())(Matchers.any(),Matchers.any())).
        thenReturn(Future.successful(HttpResponse(500)))

      val result = TestDesConnector.getResidencyStatus(Nino("LE241131B"))
      await(result).status shouldBe INTERNAL_SERVER_ERROR
    }
  }

  "DESConnector " should {

    "handle successful response when 200 is returned from EDH" in {
      when(mockHttp.GET[HttpResponse](Matchers.any())(Matchers.any(),Matchers.any())).
        thenReturn(Future.successful(HttpResponse(200)))

      val userId = "123456"
      val nino = "LE241131B"
      val resStatus = ResidencyStatus(currentYearResidencyStatus = "scotResident",
                                      nextYearForecastResidencyStatus = "scotResident")

      val result = await(TestDesConnector.sendDataToEDH(userId, nino, resStatus))
      result.status shouldBe OK
    }

    "handle successful response when 500 is returned from EDH" in {
      when(mockHttp.GET[HttpResponse](Matchers.any())(Matchers.any(),Matchers.any())).
        thenReturn(Future.successful(HttpResponse(500)))

      val userId = "123456"
      val nino = "LE241131B"
      val resStatus = ResidencyStatus(currentYearResidencyStatus = "scotResident",
                                      nextYearForecastResidencyStatus = "scotResident")

      val result = await(TestDesConnector.sendDataToEDH(userId, nino, resStatus))
      result.status shouldBe INTERNAL_SERVER_ERROR
    }
  }
}

