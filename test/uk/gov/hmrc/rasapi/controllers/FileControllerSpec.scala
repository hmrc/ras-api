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

package uk.gov.hmrc.rasapi.controllers

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.BeforeAndAfter
import org.scalatest.mock.MockitoSugar
import org.scalatestplus.play.OneAppPerSuite
import play.api.http.Status
import play.api.http.Status.UNAUTHORIZED
import play.api.libs.iteratee.Enumerator
import play.api.test.{FakeRequest, Helpers}
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.test.UnitSpec
import uk.gov.hmrc.rasapi.config.RasAuthConnector
import uk.gov.hmrc.rasapi.repository.FileData

import scala.concurrent.Future

class FileControllerSpec  extends UnitSpec with MockitoSugar with OneAppPerSuite with BeforeAndAfter {

  implicit val hc = HeaderCarrier()

  private val enrolmentIdentifier1 = EnrolmentIdentifier("PSAID", "Z123456")
  private val enrolment1 = new Enrolment(key = "HMRC-PSA-ORG", identifiers = List(enrolmentIdentifier1), state = "Activated", None)
  private val enrolmentIdentifier2 = EnrolmentIdentifier("PPID", "Z123456")
  private val enrolment2 = new Enrolment(key = "HMRC-PP-ORG", identifiers = List(enrolmentIdentifier2), state = "Activated", None)
  private val enrolments = new Enrolments(Set(enrolment1,enrolment2))

  val successfulRetrieval: Future[Enrolments] = Future.successful(enrolments)
  val mockAuthConnector = mock[RasAuthConnector]

  val fileData = FileData(length = 124L,Enumerator("TEST START ".getBytes))

  val fileController = new FileController{
    override val authConnector: AuthConnector = mockAuthConnector

    override def getFile(name: String): Future[Option[FileData]] = Some(fileData)

    override def deleteFile(name: String): Future[Boolean] = true
  }
  implicit val actorSystem = ActorSystem()
  implicit val materializer = ActorMaterializer()

  "FileController" should {
    "serve a file" when {
      "valid filename is provided" in {
        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

        val result = await(fileController.serveFile("testFile.csv").apply(FakeRequest(Helpers.GET, "/ras-api/file/getFile/:testFile")))
        result.header.status shouldBe Status.OK
        val headers = result.header.headers
        headers("Content-Length") shouldBe "124"
        headers("Content-Type") shouldBe "application/octet-stream"
        headers("Content-Disposition") shouldBe "attachment; filename=\"testFile.csv\""

        /*            val stream = result.body.dataStream.runWith(StreamConverters.asInputStream())(materializer)
       val fileOutput =  Source.fromInputStream(stream).getLines
        Logger.debug("fileout is " + fileOutput)*/

      }
    }
    "give NOT_FOUND response" when {
      val fileController = new FileController {
        override val authConnector: AuthConnector = mockAuthConnector

        override def getFile(name: String): Future[Option[FileData]] = None
      }
      "the file is not available" in {
        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)

        val result = await(fileController.serveFile("testFile.csv").apply(FakeRequest(Helpers.GET, "/ras-api/file/getFile/:testFile")))
        result.header.status shouldBe Status.NOT_FOUND
      }
    }

    "return status 401 (Unauthorised)" when {
      "a valid lookup request has been submitted with no PSA or PP enrolments" in {

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(Future.failed(new InsufficientEnrolments))

        val result = await(fileController.serveFile("testFile.csv").apply(FakeRequest(Helpers.GET, "/ras-api/file/getFile/:testFile")))
        status(result) shouldBe UNAUTHORIZED
      }
    }

    "remove a file" when {
      "already saved fileName is provided" in {
        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
        val fileName = "testFile.csv"
        val result = await(fileController.remove(fileName).apply(FakeRequest(Helpers.DELETE, s"/ras-api/file/remove/:${fileName}")))
        result.header.status shouldBe Status.OK
      }
    }

    "internalservererror" when {
      "when a file doesnt exist" in {
        val fileController = new FileController {
          override val authConnector: AuthConnector = mockAuthConnector

          override def deleteFile(name: String): Future[Boolean] = false
        }
        when(mockAuthConnector.authorise[Enrolments](any(), any())(any(), any())).thenReturn(successfulRetrieval)
        val fileName = "testFile.csv"
        val result = await(fileController.remove(fileName).apply(FakeRequest(Helpers.DELETE, s"/ras-api/file/remove/:${fileName}")))
        result.header.status shouldBe Status.INTERNAL_SERVER_ERROR
      }
    }

    "not remove file and return status 401 (Unauthorised)" when {
      "a remove request has been submitted with no PSA or PP enrolments" in {

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(), any())).thenReturn(Future.failed(new InsufficientEnrolments))

        val fileName = "testFile.csv"
        val result = await(fileController.remove(fileName).apply(FakeRequest(Helpers.DELETE, s"/ras-api/file/remove/:${fileName}")))
        status(result) shouldBe UNAUTHORIZED
      }
    }

    "not remove file return 401 (Unauthorised) for no auth session" when {
      "a remove request has been submitted with no PSA or PP enrolments" in {

        when(mockAuthConnector.authorise[Option[String]](any(), any())(any(),any())).thenReturn(Future.failed(new SessionRecordNotFound))

        val fileName = "testFile.csv"
        val result = await(fileController.remove(fileName).apply(FakeRequest(Helpers.DELETE, s"/ras-api/file/remove/:${fileName}")))
        status(result) shouldBe UNAUTHORIZED
      }
    }
  }
}