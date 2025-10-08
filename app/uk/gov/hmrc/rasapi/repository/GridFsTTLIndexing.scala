/*
 * Copyright 2025 HM Revenue & Customs
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

import org.mongodb.scala.MongoDatabase
import org.mongodb.scala.bson.{BsonInt64, Document, BsonInt32}
import org.mongodb.scala.model.IndexOptions
import play.api.Logger

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.SECONDS

trait GridFsTTLIndexing {

  val expireAfterSeconds: Int
  val log: Logger

  private lazy val LastUpdatedIndex = "lastUpdatedIndex"
  private lazy val OptExpireAfterSeconds = "expireAfterSeconds"
  protected lazy val UploadDate = "uploadDate"

  private def listAllIndexes(mdb: MongoDatabase, collectionName: String): Future[Seq[Document]] = {
    log.info(s"[GridFsTTLIndexing][retrieveLastUpdatedIndex] Searching $collectionName for all indexes.")
    mdb.getCollection(collectionName)
      .listIndexes()
      .toFuture()
  }

  private def retrieveIndex(mdb: MongoDatabase, collectionName: String, indexName: String): Future[Seq[Document]] = {
    log.info(s"[GridFsTTLIndexing][retrieveLastUpdatedIndex] Searching $collectionName for $LastUpdatedIndex")
    mdb.getCollection(collectionName)
      .listIndexes()
      .filter(index => {
        log.info(s"[GridFsTTLIndexing][retrieveLastUpdatedIndex] Index within $collectionName: $index")
        index.containsValue(indexName)
      }
    ).toFuture()
  }

  private def dropIndex(mdb: MongoDatabase, collectionName: String, indexName: String): Future[Unit] = {
    log.info(s"[GridFsTTLIndexing][checkAndEnsureTTL] Dropping the existing $indexName")
    mdb.getCollection(collectionName).dropIndex(indexName).toFuture()
  }

  private def createIndex(mdb: MongoDatabase, collectionName: String, indexName: String): Future[String] = {
    log.info(s"[GridFsTTLIndexing][checkAndEnsureTTL] Index does not exist, adding $indexName with TTL option set to $expireAfterSeconds seconds.")
    mdb.getCollection(collectionName)
        .createIndex(
          key = Document(UploadDate -> 1),
          options = IndexOptions()
            .name(indexName)
            .expireAfter(expireAfterSeconds, SECONDS)
        ).toFuture()
  }

  def checkAndEnsureTTL(mdb: MongoDatabase, collectionName: String)(implicit ec: ExecutionContext): Future[Boolean] = {
    listAllIndexes(mdb, collectionName).flatMap { indexes =>
      for {
        _ <- if (indexes.exists(index => index.containsValue(LastUpdatedIndex))) {
          dropIndex(mdb, collectionName, LastUpdatedIndex)
        } else {
          Future.successful(s"$LastUpdatedIndex does not exist.")
        }
        _ <- createIndex(mdb, collectionName, LastUpdatedIndex)
        ttlResult <- retrieveIndex(mdb, collectionName, LastUpdatedIndex).map { indexes =>
          indexes.exists(index =>
            index.containsKey(OptExpireAfterSeconds) && (
              index.values.toList.contains(BsonInt32(expireAfterSeconds)) || index.values.toList.contains(BsonInt64(expireAfterSeconds.toLong))))
        }
      } yield {
        ttlResult
      }
    }
  }
}
