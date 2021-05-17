/*
 * Copyright 2021 HM Revenue & Customs
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

package uk.gov.hmrc.rasapi.services

import org.scalatest.{BeforeAndAfter, Matchers, WordSpecLike}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.{Application, Logger, Logging}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.Awaiting
import uk.gov.hmrc.rasapi.repositories.RepositoriesHelper
import uk.gov.hmrc.rasapi.repository.RasFilesRepository

class DataCleansingServiceSpec extends WordSpecLike with Matchers with Awaiting with MockitoSugar with GuiceOneAppPerSuite
with BeforeAndAfter with Logging  {

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure("remove-chunks-data-exercise.enabled" -> true)
    .build()

  implicit lazy val rasFilesRepository: RasFilesRepository = app.injector.instanceOf[RasFilesRepository]
  before{
    await(RepositoriesHelper.rasFileRepository.removeAll()(ec))
    await(RepositoriesHelper.rasBulkOperationsRepository.removeAll()(ec))
  }
  after{
    await(RepositoriesHelper.rasFileRepository.removeAll()(ec))
    await(RepositoriesHelper.rasBulkOperationsRepository.removeAll()(ec))
  }

  lazy val dataCleansingService: DataCleansingService = app.injector.instanceOf[DataCleansingService]

  "DataCleansingService" should{

    " not remove chunks that are not orphoned" in {
      RepositoriesHelper.saveTempFile("user14","envelope14","fileId14")
      RepositoriesHelper.saveTempFile("user15","envelope15","fileId15")

      val result = await(dataCleansingService.removeOrphanedChunks())

      result.size shouldEqual 0
      await(RepositoriesHelper.rasFileRepository.remove("fileId14"))
      await(RepositoriesHelper.rasFileRepository.remove("fileId15"))
    }

    "remove orphaned chunks" in  {
      logger.warn("1 ~~~~~~~~####### Testing Data Cleansing" )
      val testFiles = RepositoriesHelper.createTestDataForDataCleansing(rasFilesRepository).map(_.id.asInstanceOf[BSONObjectID])

      val result = await(dataCleansingService.removeOrphanedChunks())
      logger.warn("7 ~~~~~~~~####### results complete" )

      result shouldEqual  testFiles
    }

  }

}
