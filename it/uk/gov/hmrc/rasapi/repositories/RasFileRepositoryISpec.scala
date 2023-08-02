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

import akka.stream.scaladsl.Sink
import mockws.MockWSHelpers.materializer
import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.rasapi.repository.{FileData, RasChunksRepository, RasFilesRepository}

import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.io.{BufferedSource, Source}

class RasFileRepositoryISpec extends PlaySpec with ScalaFutures with GuiceOneAppPerSuite with FutureAwaits with DefaultAwaitTimeout with Eventually {

  class Setup(val filename: String) {
    lazy val rasFileRepository: RasFilesRepository = app.injector.instanceOf[RasFilesRepository]
    lazy val rasChunksRepository: RasChunksRepository = app.injector.instanceOf[RasChunksRepository]
    val largeFile: File = new File("it/resources/testFiles/bulk.csv")

    val dropAll: Unit = {
      await(rasFileRepository.gridFSG.drop().head())
      fileCount mustBe 0
      chunksCount mustBe 0

      app.materializer
    }

    def fileCount: Long = {
      await(rasFileRepository.gridFSG.find().collect().head().map(res => res.length))
    }

    def chunksCount: Int =
      await(rasChunksRepository.collection.find().collect().head().map(res => res.length))

    def saveFile(): Unit = {
      await(rasFileRepository.saveFile(
        userId = "userid-1",
        envelopeId = "envelopeid-1",
        filePath = largeFile.toPath,
        fileId = filename
      ))

      eventually(Timeout(5.seconds), Interval(1.second)) {
        fileCount mustBe 1
        chunksCount mustBe 7
      }
    }
  }

  "save the file" should {
    "save the file in the files repo and chunks in the chunks repo" in new Setup("save-file-name") {
      saveFile()
    }
  }

  "fetch file" should {
    "fetch a file that was previously saved" in new Setup("fetch-file-name") {
      saveFile()

      val receiveFile: Option[FileData] = await(rasFileRepository.fetchFile(filename, "userid-1"))

      def getAll: Sink[Array[Byte], Future[Array[Byte]]] = {
        Sink.fold[Array[Byte], Array[Byte]](Array()) { (result, chunk) => result ++ chunk }
      }

      var result = new String("")

      await(receiveFile.get.data.runWith(getAll.mapMaterializedValue {
        _.map { bytes =>
          result =
            result.concat(new String(bytes))
        }
      }))

      val testSource: BufferedSource = Source.fromFile("it/resources/testFiles/bulk.csv")

      result mustBe testSource.getLines().toList.mkString("\n")

      testSource.close()
    }
  }

  "removing the file" should {
    "remove all chunks and files from the database" in new Setup("delete-file-name") {
      saveFile()

      val deleteFile: Boolean = await(rasFileRepository.removeFile(filename, "userid-1"))
      deleteFile mustBe true

      eventually(Timeout(5.seconds), Interval(1.second)) {
        fileCount mustBe 0
        chunksCount mustBe 0
      }
    }
  }
}
