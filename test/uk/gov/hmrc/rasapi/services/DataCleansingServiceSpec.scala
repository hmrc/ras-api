/*
 * Copyright 2022 HM Revenue & Customs
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

import org.mongodb.scala.{Document, MongoCollection}
import org.scalatest.BeforeAndAfter
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import play.api.{Application, Logging}
import uk.gov.hmrc.rasapi.models.ResultsFile
import uk.gov.hmrc.rasapi.repositories.RepositoriesHelper.createFile
import uk.gov.hmrc.rasapi.repository.RasFilesRepository

class DataCleansingServiceSpec extends AnyWordSpecLike with Matchers with MockitoSugar with GuiceOneAppPerSuite
with BeforeAndAfter with Logging {

  lazy val dataCleansingService: DataCleansingService = app.injector.instanceOf[DataCleansingService]
  implicit lazy val rasFilesRepository: RasFilesRepository = app.injector.instanceOf[RasFilesRepository]
  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure("remove-chunks-data-exercise.enabled" -> true)
    .build()

  "DataCleansingService" should{

    " not remove chunks that are not orphoned" in {
      await(rasFilesRepository.saveFile("user14","envelope14",createFile,"fileId14"))
      await(rasFilesRepository.saveFile("user15","envelope15",createFile,"fileId15"))
      val result = await(dataCleansingService.removeOrphanedChunks())
      result.size shouldEqual 0
    }

    "remove orphaned chunks" in  {
      val uploadedFile1: ResultsFile = await(rasFilesRepository.saveFile("user14","envelope14",createFile,"fileId14"))
      val uploadedFile2: ResultsFile = await(rasFilesRepository.saveFile("user15","envelope15",createFile,"fileId15"))
      val uploadedFiles = List(uploadedFile1.getObjectId, uploadedFile2.getObjectId)

      val filesCollection: MongoCollection[Document] = rasFilesRepository.mongoComponent.database.getCollection("resultsFiles.files")
      await(filesCollection.findOneAndDelete(Document("_id" -> uploadedFile1.getObjectId)).toFuture())
      await(filesCollection.findOneAndDelete(Document("_id" -> uploadedFile2.getObjectId)).toFuture())
      val result = await(dataCleansingService.removeOrphanedChunks())
      result shouldEqual uploadedFiles
    }
  }
}
