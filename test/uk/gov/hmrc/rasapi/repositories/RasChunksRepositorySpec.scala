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

package uk.gov.hmrc.rasapi.repositories

import org.scalatest.BeforeAndAfter
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneAppPerTest
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.rasapi.repositories.RepositoriesHelper.rasBulkOperationsRepository

import scala.concurrent.ExecutionContext.Implicits.global

class RasChunksRepositorySpec extends UnitSpec with MockitoSugar with OneAppPerTest
  with BeforeAndAfter  {

  val userId: String = "A1234567"

  before{
    RepositoriesHelper.createTestDataForDataCleansing()
   RepositoriesHelper.rasBulkOperationsRepository.removeAll()
  }
 after{
   RepositoriesHelper.rasFileRepository.removeAll()
 }

  "RasChunksRepository" should {

    "get All Chunks" in {
      val fileMetaData = await(RepositoriesHelper.saveTempFile("user222","envelope222","file222"))

      rasBulkOperationsRepository.getAllChunks()
      val res =  await(rasBulkOperationsRepository.getAllChunks())
      res.size shouldBe 1
    }
    "remove a Chunk for an ObjectId" in {
      val fileMetaData = await(RepositoriesHelper.saveTempFile("user222","envelope222","file222"))

     val result = await(rasBulkOperationsRepository.removeChunk(
        fileMetaData.id.asInstanceOf[BSONObjectID]))

      result shouldBe true
      val res1 = await(rasBulkOperationsRepository.getAllChunks())
      res1.filter(_.files_id == fileMetaData.id).headOption.isEmpty shouldBe true
    }
  }
}
