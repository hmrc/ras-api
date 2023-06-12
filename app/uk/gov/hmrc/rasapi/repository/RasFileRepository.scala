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
import akka.NotUsed
import akka.stream.scaladsl.Source
import org.mongodb.scala.bson.{Document, ObjectId}
import org.mongodb.scala.gridfs.{GridFSBucket, GridFSUploadOptions}
import org.mongodb.scala.{Observable, ObservableFuture}
import play.api.Logger
import uk.gov.hmrc.mongo.MongoComponent
import uk.gov.hmrc.mongo.play.json.PlayMongoRepository
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.models.{Chunks, ResultsFile}

import java.nio.ByteBuffer
import java.nio.file.{Files, Path}
import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

case class FileData(length: Long = 0, data: Source[Array[Byte], NotUsed] = Source.empty)

@Singleton
class RasFilesRepository @Inject()(val mongoComponent: MongoComponent,
                                   val appContext: AppContext)
                                   (implicit val ec: ExecutionContext) extends PlayMongoRepository[Chunks](
  mongoComponent = mongoComponent,
  collectionName = "rasFileStore",
  domainFormat = Chunks.format,
  indexes = Seq(),
  replaceIndexes = false
) with GridFsTTLIndexing {

  override lazy val expireAfterSeconds: Long = appContext.resultsExpriyTime
  override val log: Logger = Logger(getClass)
  private val contentType =  "text/csv"
  private val bucketName: String = "resultsFiles"
  val gridFSG: GridFSBucket = GridFSBucket(mongoComponent.database, bucketName)

  def saveFile(userId:String, envelopeId: String, filePath: Path, fileId: String): Future[ResultsFile] = {
    log.info("[RasFileRepository][saveFile] Starting to save file")

    val observableToUploadFrom: Observable[ByteBuffer] = Observable(
      Seq(ByteBuffer.wrap(Files.readAllBytes(filePath))
    ))

    val options: GridFSUploadOptions = new GridFSUploadOptions()
      .metadata(Document(
        "envelopeId" -> envelopeId,
        "fileId" -> fileId,
        "userId" -> userId,
        "contentType" -> contentType))

    gridFSG.uploadFromObservable(fileId, observableToUploadFrom, options).head().flatMap { res =>
      log.warn(s"[RasFileRepository][saveFile] Saved file $fileId for user $userId")
      checkAndEnsureTTL(mongoComponent.database, s"$bucketName.files").flatMap { ttlIndexExists =>
        if (ttlIndexExists) {
          gridFSG.find(Document("_id" -> res)).head()
        } else {
          throw new RuntimeException("Failed to checkAndEnsureTTL.")
        }
      }
    }.recover {
      case e: Throwable =>
        log.error(s"[RasFileRepository][saveFile] Error saving the file $fileId for user $userId. Exception: ${e.getMessage}")
        throw new RuntimeException(s"Failed to save file due to error: ${e.getMessage}")
    }
  }

  def fetchFile(_fileName: String, userId: String)(implicit ec: ExecutionContext): Future[Option[FileData]] = {
    log.info(s"[RasFileRepository][fetchFile] Attempting to fetch file ${_fileName} for userId ($userId).")
    gridFSG.find(Document("filename" -> _fileName)).headOption().flatMap {
      case Some(file) =>
        log.info(s"[RasFileRepository][fetchFile] Found ${_fileName} for userId ($userId).")
        gridFSG.downloadToObservable(file.getObjectId)
          .toFuture()
          .map(seq => seq.map(bb => bb.array).reduceLeft(_ ++ _))
          .map(array => {
            log.info(s"[RasFileRepository][fetchFile] Successfully downloaded ${_fileName} for userId ($userId).")
            Some(FileData(file.getLength, Source.single(array)))
          })
      case None =>
        log.warn(s"[RasFileRepository][fetchFile] Unable to find ${_fileName} for userId ($userId).")
        Future.successful(None)
    }.recover {
      case ex: Throwable =>
        log.warn(s"[RasFileRepository][fetchFile] Exception while trying to fetch file ${_fileName} for userId ($userId). Exception message: ${ex.getMessage}")
        throw new RuntimeException(s"Failed to fetch file due to error ${ex.getMessage}")
    }
  }

  def fetchResultsFile(fileId: ObjectId): Future[Option[ResultsFile]] = {
    log.debug(s"[RasFileRepository][isFileExists] Checking if file exists with id: $fileId ")
    gridFSG.find(Document("_id" -> fileId)).headOption().recover {
      case ex: Throwable =>
        log.error(s"[RasFileRepository][isFileExists] Error trying to find if parent file record exists for id: $fileId. Exception: ${ex.getMessage}")
        throw new RuntimeException(s"Failed to check file exists due to error ${ex.getMessage}")
    }
  }

  def removeFile(fileName: String, userId: String): Future[Boolean] = {
    log.debug(s"[RasFileRepository][removeFile] File to remove => fileName: $fileName for userId ($userId).")
    gridFSG.find(Document("filename" -> fileName)).headOption().flatMap {
      case Some(file) =>
        val objectId: ObjectId = file.getObjectId
        log.info(s"[RasFileRepository][removeFile] Successfully retrieved ObjectId: $objectId")
        log.info(s"[RasFileRepository][removeFile] Attempting to delete file with ObjectId: $objectId")
        for {
          _ <- gridFSG.delete(objectId).toFuture()
          maybeFile <- fetchResultsFile(objectId)
        } yield {
          maybeFile match {
            case Some(file) =>
              log.error(s"[RasFileRepository][removeFile] Error while removing file ${file.getFilename} for userId ($userId).")
              false
            case None =>
              log.info(s"[RasFileRepository][removeFile] Results file removed successfully for userId ($userId) with the file named $fileName")
              true
          }
        }
      case None =>
        log.error(s"[RasFileRepository][removeFile] No id found for filename $fileName and userId $userId")
        Future.successful(false)
    } recover {
      case ex: Throwable =>
        log.error(s"[RasFileRepository][removeFile] Error trying to remove file $fileName ${ex.getMessage} for userId ($userId).")
        throw new RuntimeException("Failed to remove file due to error" + ex.getMessage)
    }
  }
}
