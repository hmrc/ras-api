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

package uk.gov.hmrc.rasapi.repository
import org.mongodb.scala.bson.{Document, ObjectId}
import play.api.Logger
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.rasapi.models.Chunks

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RasChunksRepository @Inject()(val mongoComponent: MongoComponent)(implicit val ec: ExecutionContext) extends PlayMongoRepository[Chunks](
  mongoComponent = mongoComponent,
  collectionName = "resultsFiles.chunks",
  domainFormat = Chunks.format,
  indexes = Seq(),
  replaceIndexes = false) {

  val log: Logger = Logger(getClass)

  def getAllChunks: Future[Seq[Chunks]] = {
    collection.find(Document("files_id" -> Document("$ne" -> 1))).projection(Document("_id" -> 1, "files_id" -> 2)).collect().head().recover {
      case ex: Throwable =>
          log.error(s"[RasChunksRepository][getAllChunks] Error getting chunks: ${ex.getMessage}. Trace: $ex")
          Seq.empty
    }
  }

  def removeChunk(filesId: ObjectId): Future[Boolean] = {
    collection.deleteOne(Document("files_id" -> filesId)).head().map(res => res.getDeletedCount != 0).recover {
      case ex: Throwable =>
        log.error(s"Error when attempting to remove chunk for file id $filesId. Exception: ${ex.getMessage}. Trace: $ex")
        false
    }
  }
}
