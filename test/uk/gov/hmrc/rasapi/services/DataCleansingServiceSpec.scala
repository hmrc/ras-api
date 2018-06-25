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

package uk.gov.hmrc.rasapi.services

import org.scalatest.BeforeAndAfter
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerTest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.rasapi.repositories.RepositoriesHelper

import scala.concurrent.ExecutionContext.Implicits.global

class DataCleansingServiceSpec extends UnitSpec with MockitoSugar with OneAppPerTest
with BeforeAndAfter  {
  before{
    RepositoriesHelper.rasFileRepository.removeAll()
    RepositoriesHelper.rasBulkOperationsRepository.removeAll()

  }

  val service = new DataCleansingService()

  "DataCleansingService" should{

    "remove orphaned chunks" in  {
      val testFiles = RepositoriesHelper.createTestDataForDataCleansing().map(_.id.asInstanceOf[BSONObjectID])

      val result = await(service.removeOrphanedChunks())

      result shouldEqual  testFiles
    }
    " not remove chunks that are not orphoned" in  {
      val testData1 = await(RepositoriesHelper.saveTempFile("user14","envelope14","fileId14"))
      val testData2=  await(RepositoriesHelper.saveTempFile("user15","envelope15","fileId16"))

      val result = await(service.removeOrphanedChunks())

      result.size shouldEqual 0
    }
  }

}
