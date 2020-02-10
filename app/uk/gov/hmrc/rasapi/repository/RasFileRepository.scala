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

import java.io.FileInputStream
import java.nio.file.Path

import play.api.Logger
import play.api.libs.iteratee.Enumerator
import play.modules.reactivemongo.MongoDbConnection
import reactivemongo.api.gridfs.Implicits._
import reactivemongo.api.gridfs._
import reactivemongo.api.{BSONSerializationPack, DB, DBMetaCommands}
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.ReactiveRepository
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.models.{Chunks, ResultsFile}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{ExecutionContext, Future}


object RasRepository extends MongoDbConnection with GridFsTTLIndexing {
  // $COVERAGE-OFF$Trivial and never going to be called by a test that uses it's own object implementation
  private implicit val connection = mongoConnector.db
  override val expireAfterSeconds = AppContext.resultsExpriyTime

  lazy val filerepo: RasFileRepository = {
    val repo = new RasFileRepository(connection)
    addAllTTLs(repo.gridFSG)
    repo
  }

  lazy val chunksRepo: RasChunksRepository = new RasChunksRepository(connection)

  // $COVERAGE-ON$
}

case class FileData(length: Long = 0, data: Enumerator[Array[Byte]] = null)

class RasFileRepository(mongo: () => DB with DBMetaCommands)(implicit ec: ExecutionContext)
  extends ReactiveRepository[Chunks, BSONObjectID]("rasFileStore", mongo, Chunks.format){

  private val contentType =  "text/csv"
  val gridFSG = new GridFS[BSONSerializationPack.type](mongo(), "resultsFiles")

  def saveFile(userId:String, envelopeId: String, filePath: Path, fileId: String): Future[ResultsFile] = {
    Logger.info("[RasFileRepository] Starting to save file")
    val fileToSave = DefaultFileToSave(s"${fileId}", Some(contentType),
      metadata = BSONDocument("envelopeId" -> envelopeId, "fileId" -> fileId, "userId" -> userId))

    gridFSG.writeFromInputStream(fileToSave,new FileInputStream(filePath.toFile)).map{ res=>
      logger.warn(s"Saved File id is ${res.id} for userId ($userId)")
      res
    }.recover{case ex:Throwable =>
        Logger.error(s"error saving file -> $fileId for userId ($userId). Exception: ${ex.getMessage}")
        throw new RuntimeException(s"failed to save file due to error" + ex.getMessage)
    }
  }

  def fetchFile(_fileName: String, userId: String)(implicit ec: ExecutionContext): Future[Option[FileData]] = {
      Logger.debug(s"id in repo input is ${_fileName} for userId ($userId).")
      gridFSG.find[BSONDocument, ResultsFile](BSONDocument("filename" -> _fileName)).headOption.map {
      case Some(file) =>   logger.warn(s"file fetched ${file.id} file size = ${file.length}")
        Some(FileData(file.length, gridFSG.enumerate(file)))
      case None => logger.warn(s"file not found for userId ($userId).")
        None
    }.recover{
      case ex:Throwable =>
        Logger.error(s"error trying to fetch file ${_fileName} for userId ($userId). Exception: ${ex.getMessage}")
        throw new RuntimeException("failed to fetch file due to error" + ex.getMessage)
    }
  }

  def isFileExists(fileId:BSONObjectID): Future[Option[ResultsFile]] = {
    Logger.debug(s"Checking if file exists ${fileId} ")
    gridFSG.find[BSONDocument, ResultsFile](BSONDocument("_id" -> fileId)).headOption.recover{
      case ex:Throwable =>
        Logger.error(s"error trying to find if parent file record Exists ${fileId} for . Exception: ${ex.getMessage}")
        throw new RuntimeException("failed to check file exists due to error" + ex.getMessage)
    }
  }

  def removeFile(fileName:String, fileId:String, userId: String): Future[Boolean] = {
    Logger.debug(s"file to remove => fileName: $fileName, file Id: $fileId for userId ($userId).")
    gridFSG.find[BSONDocument, ResultsFile](BSONDocument("filename" -> fileName)).headOption.map {
      metaData =>
        if(metaData.isDefined)
          gridFSG.chunks.remove[BSONDocument](BSONDocument("files_id"-> metaData.get.id ))
    }
    gridFSG.files.remove[BSONDocument](BSONDocument("filename"-> fileName)).map{
      res =>  res.writeErrors.isEmpty match {
        case true =>
          Logger.warn(s"Results file removed successfully for userId ($userId) with the file named $fileName" )
          true
        case false =>  Logger.error(s"error while removing file ${res.writeErrors.toString} for userId ($userId).")
          false
      }
    }.recover {
      case ex: Throwable =>
        Logger.error(s"error trying to remove file ${fileName} ${ex.getMessage} for userId ($userId).")
        throw new RuntimeException("failed to remove file due to error" + ex.getMessage)
    }
  }
}