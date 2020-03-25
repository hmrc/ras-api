
package uk.gov.hmrc.rasapi.repositories

import java.io.File

import akka.stream.Materializer
import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Sink, Source => AkkaSource}
import akka.util.ByteString
import org.scalatest.concurrent.PatienceConfiguration.{Interval, Timeout}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import play.api.libs.iteratee.Iteratee
import play.api.libs.json.Json
import play.api.libs.streams.Streams
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import reactivemongo.api.ReadPreference
import reactivemongo.bson.BSONDocument
import uk.gov.hmrc.rasapi.models.ResultsFile
import uk.gov.hmrc.rasapi.repository.{FileData, RasChunksRepository, RasFilesRepository}

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
      await(rasFileRepository.gridFSG.files.delete().one(BSONDocument()))
      await(rasFileRepository.gridFSG.chunks.delete().one(BSONDocument()))
      fileCount mustBe 0
      chunksCount mustBe 0

      app.materializer
    }

    def fileCount: Int =
      await(rasFileRepository.gridFSG.files.count())

    def chunksCount: Int =
      await(rasChunksRepository.count(Json.obj(), ReadPreference.secondaryPreferred))

    def saveFile(): Unit = {
      val saveFile: ResultsFile = await(rasFileRepository.saveFile(
        userId = "userid-1",
        envelopeId = "envelopeid-1",
        filePath = largeFile.toPath,
        fileId = filename
      ))

      eventually(Timeout(5 seconds), Interval(1 second)) {
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

      val convertedToString: Future[String] = receiveFile.map{
        x => Iteratee.flatten(x.data.map(new String(_)) apply Iteratee.consume()).run
      }.getOrElse(Future.successful("failed"))

      val testSource: BufferedSource = Source.fromFile("it/resources/testFiles/bulk.csv")

      await(convertedToString) mustBe testSource.getLines().toList.mkString("\n")

      testSource.close()
    }
  }

  "removing the file" should {
    "remove all chunks and files from the database" in new Setup("delete-file-name") {
      saveFile()

      val deleteFile: Boolean = await(rasFileRepository.removeFile(filename, "fileId-1", "userid-1"))
      deleteFile mustBe true

      eventually(Timeout(5 seconds), Interval(1 second)) {
        fileCount mustBe 0
        chunksCount mustBe 0
      }
    }
  }
}
