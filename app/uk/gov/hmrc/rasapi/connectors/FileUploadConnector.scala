/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.rasapi.connectors

import java.io.InputStream

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.StreamConverters
import play.api.Mode.Mode
import play.api.{Configuration, Logger, Play}
import uk.gov.hmrc.http._
import uk.gov.hmrc.play.config.ServicesConfig
import uk.gov.hmrc.rasapi.config.WSHttp
import uk.gov.hmrc.rasapi.models.FileMetadata

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

trait FileUploadConnector extends ServicesConfig {

  val http: HttpPost
  val wsHttp: WSHttp


  override protected def mode: Mode = Play.current.mode
  override protected def runModeConfiguration: Configuration = Play.current.configuration

  lazy val serviceUrl = baseUrl("file-upload")
  lazy val fileUploadUrlSuffix = getString("file-upload-url-suffix")


  def getFile(envelopeId: String, fileId: String, userId: String)(implicit hc: HeaderCarrier): Future[Option[InputStream]] = {
    implicit val system = ActorSystem()
    implicit val materializer = ActorMaterializer()

    Logger.warn(s"Get to file-upload with URI : /file-upload/envelopes/${envelopeId}/files/${fileId}/content for userId ($userId).")
    wsHttp.buildRequestWithStream(s"$serviceUrl/$fileUploadUrlSuffix/${envelopeId}/files/${fileId}/content").map { res =>
      Some(res.body.runWith(StreamConverters.asInputStream()))
    } recover {
      case ex: Throwable => {
        Logger.error(s"Exception thrown while retrieving file / converting to InputStream for userId ($userId).", ex)
        throw new RuntimeException("Error Streaming file from file-upload service")
      }
    }
  }

  def getFileMetadata(envelopeId: String, fileId: String, userId: String)(implicit hc: HeaderCarrier): Future[Option[FileMetadata]] = {

    Logger.warn(s"Get to file-upload with URI : /file-upload/envelopes/${envelopeId}/files/${fileId}/metadata for userId ($userId).")
    wsHttp.doGet(s"$serviceUrl/$fileUploadUrlSuffix/${envelopeId}/files/${fileId}/metadata").map { res =>
      if (res.status == 200) {
        Logger.warn(s"File metadata successfully retrieved from file-upload for userId ($userId).")
        Some(res.json.as[FileMetadata])
      }
      else {
        Logger.error(s"Failed to retrieve file metadata for userId ($userId). Status ${res.status}.")
        None
      }
    } recover {
      case ex: Throwable =>
        Logger.error(s"Exception thrown while retrieving file metadata for userId ($userId). Session will continue with a default filename.", ex)
        None
    }
  }

  def deleteUploadedFile(envelopeId: String, fileId: String, userId: String)(implicit hc: HeaderCarrier): Future[Boolean] = {

    wsHttp.doDelete(s"$serviceUrl/$fileUploadUrlSuffix/${envelopeId}/files/${fileId}").map { res =>
       if(res.status == 200) {
         Logger.warn(s"user file deleted successfully from file-upload for userId ($userId).")
         true
       }
       else {
         Logger.error(s"Failed to delete user file => envelopeID: ${envelopeId}/files/${fileId} for userId ($userId)." )
        false
       }
    }.recover{
      case _ => Logger.error(s"Failed to execute delete user file => envelopeID : ${envelopeId}/files/${fileId} for userId ($userId).")
        false
    }
  }
}

object FileUploadConnector extends FileUploadConnector {

  override val http: HttpPost = WSHttp
  override val wsHttp: WSHttp = WSHttp
}




