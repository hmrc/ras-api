/*
 * Copyright 2023 HM Revenue & Customs
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

import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.models.{Chunks, ResultsFile}
import uk.gov.hmrc.rasapi.repositories.RepositoriesHelper.createFile
import uk.gov.hmrc.rasapi.repository.{RasChunksRepository, RasFilesRepository}

import scala.concurrent.ExecutionContext.Implicits.global

class RasChunksRepositorySpec extends AnyWordSpecLike with Matchers with MockitoSugar with GuiceOneAppPerSuite
  with BeforeAndAfter with DefaultPlayMongoRepositorySupport[Chunks] {

  val mockAppContext: AppContext = mock[AppContext]
  when(mockAppContext.resultsExpriyTime).thenReturn(3600)

  override lazy val repository = new RasFilesRepository(mongoComponent, mockAppContext)
  val chunksRepository = new RasChunksRepository(mongoComponent)
  val userId: String = "A1234567"

  "RasChunksRepository" should {

    "getAllChunks should return the correct amount of chunks as instances of the Chunk model" in {
      await(repository.saveFile("user222","reference222",createFile))
      val chunks = await(chunksRepository.getAllChunks)
      chunks.size shouldBe 1
    }

    "removeChunk should successfully delete a chunk when provided with an ObjectId" in {
      val uploadedFile: ResultsFile = await(repository.saveFile("user222","reference222",createFile))
      val result = await(chunksRepository.removeChunk(uploadedFile.getObjectId))
      result shouldBe true
      val chunks = await(chunksRepository.getAllChunks)
      chunks.exists(chunk => chunk.files_id == uploadedFile.getObjectId) shouldBe false
    }
  }
}
