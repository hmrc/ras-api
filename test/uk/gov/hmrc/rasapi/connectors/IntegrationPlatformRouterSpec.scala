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

import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.models.{IndividualDetails, ResidencyStatusSuccess}

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import scala.concurrent.Future

class IntegrationPlatformRouterSpec
    extends AnyWordSpecLike with Matchers with GuiceOneAppPerSuite with BeforeAndAfter with MockitoSugar {
  implicit val hc: HeaderCarrier = HeaderCarrier()

  val mockAppContext: AppContext     = mock[AppContext]
  val mockHipConnector: HipConnector = mock[HipConnector]
  val mockDesConnector: DesConnector = mock[DesConnector]

  "IntegrationPlatformRouter" must {

    val residencyStatusSuccess = ResidencyStatusSuccess(
      nino = "AB123456C",
      deathDate = Some(""),
      deathDateStatus = Some(""),
      deceasedIndicator = Some(false),
      currentYearResidencyStatus = "Uk",
      nextYearResidencyStatus = Some("Scottish")
    )

    val individualDetails = IndividualDetails(
      "AB123456C",
      "JOHN",
      "SMITH",
      LocalDate.parse("1990-02-21", DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    )

    val userId = "A123456"

    "delegate to the DES connector when hip flag is false" in {
      val integrationPlatformConnector: IntegrationPlatformRouter =
        new IntegrationPlatformRouter(mockAppContext, mockDesConnector, mockHipConnector)

      when(mockAppContext.isHip5161Enabled).thenReturn(false)
      when(mockDesConnector.getResidencyStatus(any(), any(), any()))
        .thenReturn(Future.successful(Left(residencyStatusSuccess)))

      integrationPlatformConnector.getResidencyStatus(individualDetails, userId, isBulkRequest = true)
      verify(mockDesConnector, times(1)).getResidencyStatus(individualDetails, userId, isBulkRequest = true)
    }

    "delegate to the HIP connector when hip flag is true" in {
      val integrationPlatformConnector: IntegrationPlatformRouter =
        new IntegrationPlatformRouter(mockAppContext, mockDesConnector, mockHipConnector)

      when(mockAppContext.isHip5161Enabled).thenReturn(true)
      when(mockHipConnector.getResidencyStatus(any(), any(), any()))
        .thenReturn(Future.successful(Left(residencyStatusSuccess)))

      integrationPlatformConnector.getResidencyStatus(individualDetails, userId, isBulkRequest = true)
      verify(mockHipConnector, times(1)).getResidencyStatus(individualDetails, userId, isBulkRequest = true)
    }
  }

}
