/*
 * Copyright 2020 HM Revenue & Customs
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

import java.io.{ByteArrayInputStream, InputStream}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.StreamConverters
import javax.inject.Inject
import play.api.{Configuration, Logger, Play}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.rasapi.config.{AppContext, WSHttp}
import uk.gov.hmrc.rasapi.models.FileMetadata

import scala.concurrent.{ExecutionContext, Future}

class FileUploadConnector @Inject()(
                                     val wsHttp: WSHttp,
                                     val appContext: AppContext,
                                     implicit val ec: ExecutionContext
                                   ) {

  lazy val serviceUrl: String = appContext.servicesConfig.baseUrl("file-upload")
  lazy val fileUploadUrlSuffix: String = appContext.servicesConfig.getString("file-upload-url-suffix")


  def getFile(envelopeId: String, fileId: String, userId: String)(implicit hc: HeaderCarrier): Future[Option[InputStream]] = {
    implicit val system: ActorSystem = ActorSystem()
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    Logger.info(s"[FileUploadConnector][getFile] Get to file-upload with URI : /file-upload/envelopes/${envelopeId}/files/${fileId}/content for userId ($userId).")
    wsHttp.buildRequestWithStream(s"$serviceUrl/$fileUploadUrlSuffix/${envelopeId}/files/${fileId}/content").map { res =>
      Some(res.bodyAsSource.runWith(StreamConverters.asInputStream()))
    } recover {
      case ex: Throwable => {
        Logger.error(s"[FileUploadConnector][getFile] Exception thrown while retrieving file / converting to InputStream for userId ($userId). ${ex.getMessage}}.", ex)
        throw new RuntimeException("Error Streaming file from file-upload service")
      }
    }
  }

  def getFileMetadata(envelopeId: String, fileId: String, userId: String)(implicit hc: HeaderCarrier): Future[Option[FileMetadata]] = {

    Logger.info(s"[FileUploadConnector][getFileMetadata] Get to file-upload with URI : /file-upload/envelopes/${envelopeId}/files/${fileId}/metadata for userId ($userId).")
    wsHttp.doGet(s"$serviceUrl/$fileUploadUrlSuffix/${envelopeId}/files/${fileId}/metadata").map { res =>
      if (res.status == 200) {
        Logger.info(s"[FileUploadConnector][getFileMetadata] File metadata successfully retrieved from file-upload for userId ($userId).")
        Some(res.json.as[FileMetadata])
      }
      else {
        Logger.error(s"[FileUploadConnector][getFileMetadata] Failed to retrieve file metadata for userId ($userId). Status ${res.status}.")
        None
      }
    } recover {
      case ex: Throwable =>
        Logger.error(s"[FileUploadConnector][getFileMetadata] Exception ${ex.getMessage} was thrown while retrieving file metadata for userId ($userId). Session will continue with a default filename.", ex)
        None
    }
  }

  def deleteUploadedFile(envelopeId: String, fileId: String, userId: String)(implicit hc: HeaderCarrier): Future[Boolean] = {

    wsHttp.doDelete(s"$serviceUrl/$fileUploadUrlSuffix/${envelopeId}/files/${fileId}").map { res =>
       if(res.status == 200) {
         Logger.info(s"[FileUploadConnector][deleteUploadedFile] user file deleted successfully from file-upload for userId ($userId).")
         true
       }
       else {
         Logger.error(s"[FileUploadConnector][deleteUploadedFile] Failed to delete user file => envelopeID: ${envelopeId}/files/${fileId} for userId ($userId). Status ${res.status}" )
        false
       }
    }.recover{
      case ex =>
        Logger.error(s"[FileUploadConnector][deleteUploadedFile] Exception ${ex.getMessage} was thrown while trying to delete user file => envelopeID : ${envelopeId}/files/${fileId} for userId ($userId).", ex)
        false
    }
  }
}