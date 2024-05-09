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

package uk.gov.hmrc.rasapi.api

import org.apache.pekko.util.Timeout
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatestplus.play.PlaySpec
import org.scalatestplus.play.guice.GuiceOneServerPerSuite
import play.api.Application
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.libs.ws.WSClient
import play.api.test.Helpers.OK
import play.api.test.{DefaultAwaitTimeout, FutureAwaits}
import uk.gov.hmrc.http.HttpClient
import uk.gov.hmrc.rasapi.itUtils.WireMockServerHelper
import uk.gov.hmrc.rasapi.models.ResultsFile
import uk.gov.hmrc.rasapi.repository.RasFilesRepository

import java.io.File
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.io.{BufferedSource, Source}

class FileProcessingApiISpec extends PlaySpec with ScalaFutures
  with GuiceOneServerPerSuite with FutureAwaits with DefaultAwaitTimeout with Eventually with WireMockServerHelper {

  override implicit def defaultAwaitTimeout: Timeout = 20.seconds

  override implicit lazy val app: Application = new GuiceApplicationBuilder()
    .configure("microservice.services.auth.port" -> mockPort)
    .build()

  lazy val rasFileRepository: RasFilesRepository = app.injector.instanceOf[RasFilesRepository]
  lazy val ws: WSClient = app.injector.instanceOf[WSClient]
  lazy val client:HttpClient = app.injector.instanceOf[HttpClient]

  class Setup(filename: String) {
    val largeFile: File = new File("it/resources/testFiles/bulk.csv")

    def insertFile(): ResultsFile = await(rasFileRepository.saveFile(
      userId = "userid-1",
      reference = "reference-1",
      filePath = largeFile.toPath,
    ))

    def dropAll(): Boolean = {
      await(rasFileRepository.removeFile(filename, "userid-1"))
    }
  }

  "calling the getFile" should {
    "retrieve the file" in new Setup("reference-1") {
      insertFile()

      authMocks
      val response = await(client.doGet(s"http://localhost:$port/ras-api/file/getFile/reference-1", Seq(("Authorization", "Bearer123"))))

      val testSource: BufferedSource = Source.fromFile("it/resources/testFiles/bulk.csv")

      response.status mustBe OK
      response.body mustBe testSource.getLines().toList.mkString("\n")
      response.header("Content-Type") mustBe Some("application/csv")

      testSource.close()

      dropAll()
    }
  }

}
