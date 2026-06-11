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

package uk.gov.hmrc.rasapi.config

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.play.guice.GuiceOneAppPerSuite

import scala.concurrent.duration.{Duration, DurationInt}

class AppContextSpec extends AnyWordSpecLike with Matchers with GuiceOneAppPerSuite {

  lazy val appContext: AppContext = app.injector.instanceOf[AppContext]

  "AppContext" should {

    "expose appName from configuration" in {
      appContext.appName shouldBe "ras-api"
    }

    "expose apiContext from configuration" in {
      appContext.apiContext shouldBe "individuals/relief-at-source"
    }

    "expose apiStatus from configuration" in {
      appContext.apiStatus shouldBe "BETA"
    }

    "expose endpointsEnabled from configuration" in {
      appContext.endpointsEnabled shouldBe true
    }

    "expose desAuthToken from configuration" in {
      appContext.desAuthToken should not be empty
    }

    "expose desUrlHeaderEnv from configuration" in {
      appContext.desUrlHeaderEnv shouldBe "local"
    }

    "expose resultsExpriyTime from configuration" in {
      appContext.resultsExpriyTime shouldBe 3600
    }

    "expose allowNoNextYearStatus toggle from configuration" in {
      appContext.allowNoNextYearStatus shouldBe false
    }

    "expose allowDefaultRUK toggle from configuration" in {
      appContext.allowDefaultRUK shouldBe true
    }

    "expose retryEnabled toggle from configuration" in {
      appContext.retryEnabled shouldBe false
    }

    "expose bulkRetryEnabled toggle from configuration" in {
      appContext.bulkRetryEnabled shouldBe false
    }

    "expose requestRetryLimit from configuration" in {
      appContext.requestRetryLimit shouldBe 3
    }

    "expose retryDelay from configuration" in {
      appContext.retryDelay shouldBe 700
    }

    "expose deceasedStatus from configuration" in {
      appContext.deceasedStatus shouldBe "DECEASED"
    }

    "expose tooManyRequestsStatus from configuration" in {
      appContext.tooManyRequestsStatus shouldBe "TOO_MANY_REQUESTS"
    }

    "expose matchingFailedStatus from configuration" in {
      appContext.matchingFailedStatus shouldBe "STATUS_UNAVAILABLE"
    }

    "expose serviceUnavailableStatus from configuration" in {
      appContext.serviceUnavailableStatus shouldBe "SERVICE_UNAVAILABLE"
    }

    "expose doNotReProcessStatus from configuration" in {
      appContext.doNotReProcessStatus shouldBe "DO_NOT_RE_PROCESS"
    }

    "expose fileProcessingMatchingFailedStatus from configuration" in {
      appContext.fileProcessingMatchingFailedStatus shouldBe "cannot_provide_status"
    }

    "expose apiv1Status from configuration" in {
      appContext.apiv1Status shouldBe "RETIRED"
    }

    "expose apiv2Status from configuration" in {
      appContext.apiv2Status shouldBe "BETA"
    }

    "expose fileProcessingInternalServerErrorStatus from configuration" in {
      appContext.fileProcessingInternalServerErrorStatus shouldBe "problem-getting-status"
    }

    "expose internalServerErrorStatus from configuration" in {
      appContext.internalServerErrorStatus shouldBe "INTERNAL_SERVER_ERROR"
    }

    "expose removeChunksDataExerciseEnabled toggle from configuration" in {
      appContext.removeChunksDataExerciseEnabled shouldBe true
    }

    "expose apiV2_0Enabled toggle from configuration" in {
      appContext.apiV2_0Enabled shouldBe true
    }

    "expose rasFileSessionTTL from configuration" in {
      val ttl: Duration = appContext.rasFileSessionTTL
      ttl shouldBe 4.days
    }
  }

}
