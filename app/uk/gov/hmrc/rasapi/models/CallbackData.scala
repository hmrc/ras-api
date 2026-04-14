/*
 * Copyright 2026 HM Revenue & Customs
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

case class FileMetadata(id: String, name: Option[String], created: Option[String])

object FileMetadata {
  given format: OFormat[FileMetadata] = Json.format[FileMetadata]
}

case class UploadDetails(uploadTimestamp: Instant, checksum: String, fileMimeType: String, fileName: String, size: Int)

case class FailureDetails(failureReason: String, message: String)

object FailureDetails {
  given formats: OFormat[FailureDetails] = Json.format[FailureDetails]
}

object UploadDetails {
  given formats: OFormat[UploadDetails] = Json.format[UploadDetails]
}

case class UpscanCallbackData(
  reference: String,
  downloadUrl: Option[String],
  fileStatus: String,
  uploadDetails: Option[UploadDetails],
  failureDetails: Option[FailureDetails]
) {
  val failureReason: String = this.failureDetails.getOrElse(FailureDetails("unknown", "")).failureReason
  val message: String       = this.failureDetails.getOrElse(FailureDetails("unknown", "")).message

  def toFileMetadata: FileMetadata =
    FileMetadata(
      this.reference,
      Some(this.uploadDetails.getOrElse(UploadDetails(Instant.now(), "", "", "", 0)).fileName),
      Some(this.uploadDetails.getOrElse(UploadDetails(Instant.now(), "", "", "", 0)).uploadTimestamp.toString)
    )

}

object UpscanCallbackData {
  given formats: OFormat[UpscanCallbackData] = Json.format[UpscanCallbackData]
}

case class ResultsFileMetaData(id: String, filename: String, uploadDate: Long, chunkSize: Int, length: Long)

object ResultsFileMetaData {
  given formats: OFormat[ResultsFileMetaData] = Json.format[ResultsFileMetaData]
}

case class Chunks(_id: ObjectId, files_id: ObjectId)

object Chunks {
  given objectIdformats: Format[ObjectId] = MongoFormats.objectIdFormat
  given format: OFormat[Chunks]           = Json.format[Chunks]
}

case class FileSession(
  userFile: Option[UpscanCallbackData],
  resultsFile: Option[ResultsFileMetaData],
  userId: String,
  uploadTimeStamp: Option[Long],
  fileMetadata: Option[FileMetadata]
)

object FileSession {
  given format: OFormat[FileSession] = Json.format[FileSession]
}
