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

package uk.gov.hmrc.rasapi.repositories

import java.io.{BufferedWriter, ByteArrayInputStream, FileWriter}
import java.nio.file.{Files, Path}

import play.api.{Application, Logger}
import play.api.libs.iteratee.{Enumerator, Iteratee}
import play.api.mvc.ControllerComponents
import play.api.test.Helpers.stubControllerComponents
import reactivemongo.api.DefaultDB
import reactivemongo.api.commands.WriteResult
import reactivemongo.bson.BSONDocument
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.rasapi.models.ResultsFile
import uk.gov.hmrc.rasapi.repository.{RasChunksRepository, RasFilesRepository}
import uk.gov.hmrc.rasapi.services.RasFileWriter

import scala.concurrent.{ExecutionContext, Future}
import scala.io.Source
import scala.util.Random

object TestFileWriter extends RasFileWriter {

  def generateFile(data: Iterator[Any]) :Path = {
    val file = Files.createTempFile(Random.nextInt().toString,".csv")
    val outputStream = new BufferedWriter(new FileWriter(file.toFile))
    try {
      data.foreach { line => outputStream.write(line.toString)
        outputStream.newLine
      }

      file
    }
    catch {
      case ex: Throwable => Logger.error("Error creating file" + ex.getMessage)
        outputStream.close ;throw new RuntimeException("Exception in generating file" + ex.getMessage)
    }
    finally outputStream.close
  }
}

object RepositoriesHelper extends MongoSpecSupport with UnitSpec {

  val cc: ControllerComponents = stubControllerComponents()
  implicit val ec: ExecutionContext = cc.executionContext

  val hostPort = System.getProperty("mongoHostPort", "127.0.0.1:27017")
  override val  databaseName = "ras-api"
  val mongoConnector = MongoConnector(s"mongodb://$hostPort/$databaseName").db

  def rasFileRepository(implicit rasFilesRepository: RasFilesRepository): RasFileRepositoryTest = new RasFileRepositoryTest(rasFilesRepository)

  val resultsArr = Array("456C,John,Smith,1990-02-21,nino-INVALID_FORMAT",
    "AB123456C,John,Smith,1990-02-21,NOT_MATCHED",
    "AB123456C,John,Smith,1990-02-21,otherUKResident,scotResident")

  val tempFile: Array[Any] = Array("TestMe START",1234345,"sdfjdkljfdklgj", "Test Me END")


  lazy val createFile: Path = {
    await(TestFileWriter.generateFile(resultsArr.iterator))
  }

  def saveTempFile(userID: String, envelopeID: String, fileId: String)(implicit rasFileRepository: RasFilesRepository): ResultsFile = {
    val filePath = await(TestFileWriter.generateFile(tempFile.iterator))
    await(rasFileRepository.saveFile(userID, envelopeID, filePath, fileId))
  }

  case class FileData( data: Enumerator[Array[Byte]] = null)

  def getAll: Iteratee[Array[Byte], Array[Byte]] = Iteratee.consume[Array[Byte]]()

  class RasFileRepositoryTest(rfr: RasFilesRepository)(implicit ec: ExecutionContext)
    extends RasFilesRepository(
      rfr.mongoComponent,
      rfr.appContext) with MongoSpecSupport {

    override implicit val mongo: () => DefaultDB = mongoConnectorForTest.db

    def getFile(storedFile: ResultsFile): Iterator[String] = {
      val inputStream = gridFSG.enumerate(storedFile) run getAll map { bytes =>
        new ByteArrayInputStream(bytes)
      }
      Source.fromInputStream(inputStream).getLines
    }

    def remove(fileName:String): Future[WriteResult] = {gridFSG.files.delete.one(BSONDocument("filename"-> fileName))}
  }

  def rasBulkOperationsRepository(implicit app: Application): RasChunksRepository = app.injector.instanceOf[RasChunksRepository]

  def createTestDataForDataCleansing(rasFilesRepository: RasFilesRepository): List[ResultsFile] = {
    val fileNames = List("file1212","file1313")

    for{
      file1 <- await(RepositoriesHelper.saveTempFile("user1212","envelope1212",fileNames.head)(rasFilesRepository))
      file2 <- await(RepositoriesHelper.saveTempFile("user1313","envelope1313",fileNames.last)(rasFilesRepository))
      remFile1 <- await(RepositoriesHelper.rasFileRepository(rasFilesRepository).remove((fileNames.head)))
      remFile2 <- await(RepositoriesHelper.rasFileRepository(rasFilesRepository).remove((fileNames.last)))
    } yield List(file1,file2)
  }

}



