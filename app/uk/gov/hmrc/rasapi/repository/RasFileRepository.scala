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

import java.io.FileInputStream
import java.nio.file.Path

import javax.inject.Inject
import org.joda.time.DateTime
import play.api.libs.iteratee.Enumerator
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.BSONSerializationPack
import reactivemongo.api.gridfs.DefaultFileToSave.FileName.SomeFileName
import reactivemongo.api.gridfs.Implicits._
import reactivemongo.api.gridfs._
import reactivemongo.bson.{BSONDocument, BSONObjectID, BSONValue}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.models.{Chunks, ResultsFile}

import scala.concurrent.{ExecutionContext, Future}

case class FileData(length: Long = 0, data: Enumerator[Array[Byte]] = null)

class RasFilesRepository @Inject()(
                                   val mongoComponent: ReactiveMongoComponent,
                                   val appContext: AppContext,
                                   implicit val ec: ExecutionContext
                             ) extends ReactiveRepository[Chunks, BSONObjectID](
  collectionName = "rasFileStore",
  mongo = mongoComponent.mongoConnector.db,
  domainFormat = Chunks.format
) with GridFsTTLIndexing {

  override lazy val expireAfterSeconds: Long = appContext.resultsExpriyTime
  private val contentType =  "text/csv"

  val gridFSG: GridFS[BSONSerializationPack.type] =
    GridFS[BSONSerializationPack.type](mongoComponent.mongoConnector.db(), "resultsFiles")

  addAllTTLs(gridFSG)

  def generateFileToSave(fileId: String, contentType: String, envelopeId: String, userId: String): FileToSave[BSONSerializationPack.type, BSONValue] =
    DefaultFileToSave(
      filename = Some(fileId),
      contentType = Some(contentType),
      uploadDate = Some(DateTime.now().getMillis),
      metadata = BSONDocument("envelopeId" -> envelopeId, "fileId" -> fileId, "userId" -> userId)
    )

  def saveFile(userId:String, envelopeId: String, filePath: Path, fileId: String): Future[ResultsFile] = {
    logger.info("[RasFileRepository][saveFile] Starting to save file")

    gridFSG.writeFromInputStream(generateFileToSave(fileId, contentType, envelopeId, userId), new FileInputStream(filePath.toFile)).map{ res =>
      logger.warn(s"Saved File id is ${res.id} for userId ($userId)")
      res
    }.recover{case ex:Throwable =>
        logger.error(s"error saving file -> $fileId for userId ($userId). Exception: ${ex.getMessage}")
        throw new RuntimeException(s"failed to save file due to error" + ex.getMessage)
    }
  }

  def fetchFile(_fileName: String, userId: String)(implicit ec: ExecutionContext): Future[Option[FileData]] = {
      logger.info(s"id in repo input is ${_fileName} for userId ($userId).")

      gridFSG.find[BSONDocument, ResultsFile](BSONDocument("filename" -> _fileName)).headOption.map {
      case Some(file) =>   logger.info(s"file fetched ${file.id} file size = ${file.length}")
        Some(FileData(file.length, gridFSG.enumerate(file)))
      case None => logger.warn(s"file not found for userId ($userId).")
        None
    }.recover{
      case ex:Throwable =>
        logger.error(s"error trying to fetch file ${_fileName} for userId ($userId). Exception: ${ex.getMessage}")
        throw new RuntimeException("failed to fetch file due to error" + ex.getMessage)
    }
  }

  def isFileExists(fileId:BSONObjectID): Future[Option[ResultsFile]] = {
    logger.debug(s"Checking if file exists ${fileId} ")
    gridFSG.find[BSONDocument, ResultsFile](BSONDocument("_id" -> fileId)).headOption.recover{
      case ex:Throwable =>
        logger.error(s"error trying to find if parent file record Exists ${fileId} for . Exception: ${ex.getMessage}")
        throw new RuntimeException("failed to check file exists due to error" + ex.getMessage)
    }
  }

  private def getBsonObjectIdFromFileName(filename: String): Future[Option[BSONObjectID]] =
    gridFSG.files.find(BSONDocument("filename" -> filename), Some(BSONDocument("_id" -> 1))).one[BSONDocument].map {
      optionalDocument => optionalDocument.flatMap { document =>
        document.getAs[BSONObjectID]("_id")
      }
    }

  // TODO: fileName and fileId are the same thing
  def removeFile(fileName:String, fileId:String, userId: String): Future[Boolean] = {
    logger.debug(s"file to remove => fileName: $fileName, file Id: $fileId for userId ($userId).")
    getBsonObjectIdFromFileName(fileName).flatMap {
      case Some(bsonObjectId) =>
        logger.info(s"[RasFileRepository][removeFile] successfully got id for file. BSONObjectId: ${bsonObjectId.stringify}")
        gridFSG.remove(bsonObjectId).map {
          res =>
            if(res.writeErrors.isEmpty){
              logger.info(s"Results file removed successfully for userId ($userId) with the file named $fileName")
            } else {
              logger.error(s"error while removing file ${res.writeErrors.toString} for userId ($userId).")
            }
            res.writeErrors.isEmpty
        }
      case None =>
        logger.error(s"[RasFileRepository][removeFile] no id found for filename $fileName and userId $userId")
        Future.successful(false)
    } recover {
      case ex: Throwable =>
        logger.error(s"error trying to remove file ${fileName} ${ex.getMessage} for userId ($userId).")
        throw new RuntimeException("failed to remove file due to error" + ex.getMessage)
    }
  }
}
