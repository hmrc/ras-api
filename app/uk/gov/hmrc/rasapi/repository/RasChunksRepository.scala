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

package uk.gov.hmrc.rasapi.repository

import javax.inject.Inject
import play.api.Logger
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.{Cursor, DB, DBMetaCommands}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.rasapi.models.Chunks

import scala.concurrent.{ExecutionContext, Future}

class RasChunksRepository @Inject()(
                                     val mongoComponent: ReactiveMongoComponent,
                                     implicit val ec: ExecutionContext
                                   ) extends ReactiveRepository[Chunks, BSONObjectID](
    collectionName = "resultsFiles.chunks",
    mongo = mongoComponent.mongoConnector.db,
    domainFormat = Chunks.format){

  def getAllChunks(): Future[Seq[Chunks]] ={
    val query = BSONDocument("files_id" -> BSONDocument("$ne" -> "1"))
    Logger.debug("********Remove chunks :Started*********")

    // only fetch the id and files-id field for the result documents
    val projection = BSONDocument("_id" -> 1, "files_id" -> 2)
    collection.find(query,projection).cursor[Chunks]().collect[Seq](Int.MaxValue, Cursor.FailOnError()).recover {
      case ex: Throwable =>
        Logger.error(s"[RasChunksRepository][getAllChunks] Error fetching chunks  ${ex.getMessage}.", ex)
        Seq.empty
    }

  }

  def removeChunk(filesId:BSONObjectID): Future[Boolean] = {
    val query = BSONDocument("files_id" -> filesId)
    collection.remove(query).map(res=> res.writeErrors.isEmpty).recover{
      case ex:Throwable =>
        Logger.error(s"[RasChunksRepository][removeChunk] error removing chunk ${filesId} with the exception ${ex.getMessage}.")
        false
    }
  }
}
