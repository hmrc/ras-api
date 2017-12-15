/*
 * Copyright 2017 HM Revenue & Customs
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
import reactivemongo.api.BSONSerializationPack
import reactivemongo.api.gridfs.ReadFile
import reactivemongo.bson.BSONValue
import uk.gov.hmrc.rasapi.models.FileDetails.RASFile

case class FileDetails (envelopeId:String, FileId:String, fileName:String,resultsFilePath:String, status:String="success")

case class ResultsFile(fileDetails:RASFile)


object FileDetails
{
  type RASFile = ReadFile[BSONSerializationPack.type, BSONValue]
  implicit val  fileFormats = Json.format[FileDetails]
}



