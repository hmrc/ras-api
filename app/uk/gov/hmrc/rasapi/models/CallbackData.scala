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

package uk.gov.hmrc.rasapi.models

import play.api.libs.json.Json
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.mongo.json.ReactiveMongoFormats

case class FileMetadata(id: String, name: Option[String], created: Option[String])

object FileMetadata {
  implicit val format = Json.format[FileMetadata]
}

case class CallbackData(envelopeId: String, fileId: String, status: String, reason: Option[String])

object CallbackData {
  implicit val formats = Json.format[CallbackData]
}

case class ResultsFileMetaData (id: String, filename: Option[String],uploadDate: Option[Long], chunkSize: Int, length: Long)

object ResultsFileMetaData {
  implicit val formats = Json.format[ResultsFileMetaData]

}

case class Chunks(_id:BSONObjectID, files_id:BSONObjectID)

object Chunks {
  implicit val objectIdformats = ReactiveMongoFormats.objectIdFormats
  implicit  val format = Json.format[Chunks]
}

case class FileSession(userFile: Option[CallbackData], resultsFile: Option[ResultsFileMetaData], userId: String, uploadTimeStamp : Option[Long], fileMetadata: Option[FileMetadata])

object FileSession {
  implicit val format = Json.format[FileSession]
}