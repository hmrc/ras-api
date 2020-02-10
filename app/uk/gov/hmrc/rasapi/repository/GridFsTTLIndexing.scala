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
import reactivemongo.api.BSONSerializationPack
import reactivemongo.api.collections.GenericCollection
import reactivemongo.api.gridfs.GridFS
import reactivemongo.api.indexes.{Index, IndexType}
import reactivemongo.bson.{BSONDocument, BSONLong}

import scala.concurrent.Future

trait GridFsTTLIndexing {

  val expireAfterSeconds: Long

  private lazy val LastUpdatedIndex = "lastUpdatedIndex"
  private lazy val OptExpireAfterSeconds = "expireAfterSeconds"
  protected lazy val UploadDate = "uploadDate"

  def addAllTTLs(gfs : GridFS[BSONSerializationPack.type])(implicit ec: scala.concurrent.ExecutionContext): Future[Seq[Boolean]] = {
    Future.sequence(Seq(
      addFilesTTL(gfs),
      addChunksTTL(gfs)
    ))
  }

  def addChunksTTL(gfs : GridFS[BSONSerializationPack.type])(implicit ec: scala.concurrent.ExecutionContext): Future[Boolean] = {
    addTTL(gfs.chunks)
  }

  def addFilesTTL(gfs : GridFS[BSONSerializationPack.type])(implicit ec: scala.concurrent.ExecutionContext): Future[Boolean] = {
    addTTL(gfs.files)
  }

  private def addTTL(collection: GenericCollection[BSONSerializationPack.type])(implicit ec: scala.concurrent.ExecutionContext): Future[Boolean] = {
    import reactivemongo.bson.DefaultBSONHandlers._
    val indexes = collection.indexesManager.list()
    indexes.flatMap {
      idxs => {

        val idxToUpdate = idxs.find(index =>
          index.eventualName == LastUpdatedIndex
            && index.options.getAs[BSONLong](OptExpireAfterSeconds).getOrElse(BSONLong(expireAfterSeconds)).as[Long] != expireAfterSeconds)

        if (idxToUpdate.isDefined) {
          for {
            deleted <- collection.indexesManager.drop(idxToUpdate.get.eventualName)

            updated <- ensureLastUpdated(collection)
          } yield updated
        }
        else {
          ensureLastUpdated(collection)
        }
      }
    }
    Logger.info(s"Creating time to live for entries in ${collection.name} to $expireAfterSeconds seconds")
    ensureLastUpdated(collection)
  }

  private def ensureLastUpdated(collection : GenericCollection[BSONSerializationPack.type])(implicit ec: scala.concurrent.ExecutionContext) = {
    Logger.debug("Indexes ensured by creating if they doesn't exist")
    collection.indexesManager.ensure(
      Index(
        key = Seq(UploadDate -> IndexType.Ascending),
        name = Some(LastUpdatedIndex),
        options = BSONDocument(OptExpireAfterSeconds -> expireAfterSeconds)
      )
    )
  }
}
