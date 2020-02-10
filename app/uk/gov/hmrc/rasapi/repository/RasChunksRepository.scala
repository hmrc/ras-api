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

package uk.gov.hmrc.rasapi.repository

import play.api.Logger
import reactivemongo.api.{DB, DBMetaCommands}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.rasapi.models.Chunks

import scala.concurrent.ExecutionContext

class RasChunksRepository(mongo: () => DB with DBMetaCommands)(implicit ec: ExecutionContext)
  extends ReactiveRepository[Chunks, BSONObjectID]("resultsFiles.chunks", mongo, Chunks.format){
  def getAllChunks() ={
    val query = BSONDocument("files_id" -> BSONDocument("$ne" -> "1"))
    Logger.debug("********Remove chunks :Started*********")

    // only fetch the id and files-id field for the result documents
    val projection = BSONDocument("_id"-> 1,"files_id" -> 2)
    collection.find(query,projection).cursor[Chunks]().collect[Seq]().recover {
      case ex: Throwable =>
        Logger.error(s"error fetching chunks  ${ex.getMessage}.")
        Seq.empty
    }

  }

  def removeChunk(filesId:BSONObjectID) = {
    val query = BSONDocument("files_id" -> filesId)
    collection.remove(query).map(res=> res.writeErrors.isEmpty).recover{
      case ex:Throwable =>
        Logger.error(s"error removing chunk ${filesId} with the exception ${ex.getMessage}.")
        false
    }
  }
}