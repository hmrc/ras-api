
package uk.gov.hmrc.rasapi.api

import java.io.File

import akka.util.Timeout
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.{GuiceOneAppPerSuite, GuiceOneServerPerSuite}
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.{WSClient, WSResponse}
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.rasapi.itUtils.WireMockServerHelper
import uk.gov.hmrc.rasapi.models.ResultsFile
import uk.gov.hmrc.rasapi.repository.RasFilesRepository
import play.api.test.Helpers.OK

import scala.io.{BufferedSource, Source}
import scala.concurrent.duration._

class FileProcessingApiISpec extends PlaySpec with ScalaFutures
  with GuiceOneServerPerSuite with FutureAwaits with DefaultAwaitTimeout with Eventually with WireMockServerHelper {

  override implicit def defaultAwaitTimeout: Timeout = 20 seconds

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure("microservice.services.auth.port" -> mockPort)
    .build()

  lazy val rasFileRepository: RasFilesRepository = app.injector.instanceOf[RasFilesRepository]
  lazy val ws: WSClient = app.injector.instanceOf[WSClient]

  class Setup(filename: String) {
    val largeFile: File = new File("it/resources/testFiles/bulk.csv")

    def insertFile(): ResultsFile = await(rasFileRepository.saveFile(
      userId = "userid-1",
      envelopeId = "envelopeid-1",
      filePath = largeFile.toPath,
      fileId = filename
    ))

    def dropAll(): Boolean = {
      await(rasFileRepository.removeFile(filename, filename, "userid-1"))
    }
  }

  "calling the getFile" should {
    "retrieve the file" in new Setup("file-name-1") {
      insertFile()

      authMocks

      val response: WSResponse = await(ws.url(s"http://localhost:$port/ras-api/file/getFile/file-name-1").get())

      val testSource: BufferedSource = Source.fromFile("it/resources/testFiles/bulk.csv")

      response.status mustBe OK
      response.body mustBe testSource.getLines().toList.mkString("\n")
      response.header("Content-Type") mustBe Some("application/csv")

      testSource.close()

      dropAll()
    }
  }

}
