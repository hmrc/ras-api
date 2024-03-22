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

package uk.gov.hmrc.rasapi.models

import org.mongodb.scala.bson.ObjectId
import play.api.libs.json.{Format, Json, OFormat}
import uk.gov.hmrc.mongo.play.json.formats.MongoFormats

import java.time.Instant
import java.util.regex.{Matcher, Pattern}
import scala.util.matching.Regex

case class FileMetadata(id: String, name: Option[String], created: Option[String])

object FileMetadata {
  def fromCallbackData(cd: UpscanCallbackData): FileMetadata =
    FileMetadata(
      cd.reference,
      Some(cd.uploadDetails.getOrElse(UploadDetails.empty).fileName),
      Some(cd.uploadDetails.getOrElse(UploadDetails.empty).uploadTimestamp.toString)
    )

  implicit val format: OFormat[FileMetadata] = Json.format[FileMetadata]
}

case class CallbackData(downloadUrl: String, fileId: String, status: String, reason: Option[String], uploadDetails: Option[UploadDetails] = None)

case class UploadDetails(uploadTimestamp: Instant, checksum: String, fileMimeType: String, fileName: String, size: Int)

case class FailureDetails(failureReason: String, message: String)
  object FailureDetails{
    implicit val formats: OFormat[FailureDetails] = Json.format[FailureDetails]
  }

object UploadDetails {
  val empty: UploadDetails = UploadDetails(Instant.now(), "", "", "", 0)
  implicit val formats: OFormat[UploadDetails] = Json.format[UploadDetails]
}
case class UpscanCallbackData(reference: String, downloadUrl: Option[String], fileStatus: String, uploadDetails: Option[UploadDetails], failureDetails: Option[FailureDetails]) {
  val fileIdPattern: Regex = """[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}""".r

  val failureReason = this.failureDetails.getOrElse(FailureDetails("unknown", "")).failureReason
  val message = this.failureDetails.getOrElse(FailureDetails("unknown", "")).message

  //val fileId: String = fileIdPattern.findFirstIn(downloadUrl).getOrElse(throw new IllegalArgumentException("dupa o!"))
}

object CallbackData {

  def fromUpscanCallbackData(ucd: UpscanCallbackData): CallbackData =
    CallbackData(
      ucd.downloadUrl.get,
      ucd.reference,
      ucd.fileStatus,
      None,
      Some(ucd.uploadDetails.get)
    )

  implicit val formats: OFormat[CallbackData] = Json.format[CallbackData]
}

object UpscanCallbackData {
  implicit val formats: OFormat[UpscanCallbackData] = Json.format[UpscanCallbackData]
}

case class ResultsFileMetaData (id: String, filename: String,uploadDate: Long, chunkSize: Int, length: Long)

object ResultsFileMetaData {
  implicit val formats: OFormat[ResultsFileMetaData] = Json.format[ResultsFileMetaData]
}

case class Chunks(_id: ObjectId, files_id: ObjectId)

object Chunks {
  implicit val objectIdformats: Format[ObjectId] = MongoFormats.objectIdFormat
  implicit  val format: OFormat[Chunks] = Json.format[Chunks]
}

case class FileSession(userFile: Option[UpscanCallbackData],
                       resultsFile: Option[ResultsFileMetaData],
                       userId: String,
                       uploadTimeStamp : Option[Long],
                       fileMetadata: Option[FileMetadata]
                      )

object FileSession {
  implicit val format: OFormat[FileSession] = Json.format[FileSession]
}
