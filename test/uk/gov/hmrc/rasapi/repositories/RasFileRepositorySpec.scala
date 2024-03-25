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

import org.bson.types.ObjectId
import org.mockito.ArgumentMatchers.{any, anyString}
import org.mockito.Mockito.when
import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.gridfs.{GridFSBucket, GridFSUploadObservable}
import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.Eventually
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.Logging
import play.api.test.Helpers.{await, defaultAwaitTimeout}
import uk.gov.hmrc.mongo.test.DefaultPlayMongoRepositorySupport
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.models.{Chunks, ResultsFile}
import uk.gov.hmrc.rasapi.repositories.RepositoriesHelper.createFile
import uk.gov.hmrc.rasapi.repository.{FileData, RasFilesRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}

class RasFileRepositorySpec extends AnyWordSpecLike with Matchers with MockitoSugar with GuiceOneAppPerSuite
  with BeforeAndAfter with Eventually with Logging with DefaultPlayMongoRepositorySupport[Chunks] {

  val mockAppContext: AppContext = mock[AppContext]
  when(mockAppContext.resultsExpriyTime).thenReturn(3600)

  override lazy val repository = new RasFilesRepository(mongoComponent, mockAppContext)
  val userId: String = "A1234567"

  "RasFileRepository" should {
    "saveFile should successfully save a file in storage" in {
      val uploadedFile: ResultsFile = await(repository.saveFile("user111","reference111",createFile,"file111"))
      uploadedFile.getFilename shouldBe "file111"
      uploadedFile.getMetadata.getString("reference") shouldBe "reference111"
      uploadedFile.getMetadata.getString("fileId") shouldBe "file111"
      uploadedFile.getMetadata.getString("userId") shouldBe "user111"
      uploadedFile.getMetadata.getString("contentType") shouldBe "text/csv"
    }

    "saveFile should return a RuntimeException if checkAndEnsureTTL is false" in {
      val testRepository = new RasFilesRepository(mongoComponent, mockAppContext) {
        override val gridFSG: GridFSBucket = mock[GridFSBucket]
        override def checkAndEnsureTTL(mdb: MongoDatabase, collectionName: String)(implicit ec: ExecutionContext): Future[Boolean] = Future.successful(false)
      }
      val mockGridFSUploadObservable: GridFSUploadObservable[ObjectId] = mock[GridFSUploadObservable[ObjectId]]

      when(testRepository.gridFSG.uploadFromObservable(anyString(), any(), any())).thenReturn(mockGridFSUploadObservable)
      when(mockGridFSUploadObservable.head()).thenReturn(Future.successful(new ObjectId("123456789012345678901234")))

      intercept[RuntimeException] {
        await(testRepository.saveFile("user124","reference124",createFile,"file444"))
      }.getMessage shouldBe "Failed to save file due to error: Failed to checkAndEnsureTTL."
    }

    "fetchFile should return an instance of FileData" in {
      val uploadedFile: ResultsFile = await(repository.saveFile("user123","reference123",createFile,"file123"))
      val fetchedFileData: Option[FileData] = await(repository.fetchFile(uploadedFile.getFilename, userId))
      fetchedFileData.isDefined shouldBe true
      fetchedFileData.get.length shouldBe uploadedFile.getLength
    }

    "removeFile should successfully remove a file from storage" in {
      val uploadedFile: ResultsFile = await(repository.saveFile("user222","reference222",createFile,"file222"))
      val removedFileResult: Boolean = await(repository.removeFile(uploadedFile.getFilename, userId))
      removedFileResult shouldBe true
    }

    "fetchResultsFile should return an instance of ResultsFile" in {
      val uploadedFile: ResultsFile = await(repository.saveFile("user124","reference124",createFile,"file444"))
      val fetchedFile: Option[ResultsFile] = await(repository.fetchResultsFile(uploadedFile.getObjectId))
      fetchedFile.isDefined shouldBe true
    }
  }
}
