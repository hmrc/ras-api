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

package uk.gov.hmrc.rasapi.services

import javax.inject.Inject
import play.api.Logger
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.repository.{RasChunksRepository, RasFilesRepository}

import scala.concurrent.{ExecutionContext, Future}

class DataCleansingService @Inject()(
                                      appContext: AppContext,
                                      chunksRepo: RasChunksRepository,
                                      fileRepo: RasFilesRepository)
                                    (implicit val ec: ExecutionContext) {

  def removeOrphanedChunks(): Future[Seq[BSONObjectID]] = if(appContext.removeChunksDataExerciseEnabled){
    Logger.info("[data-cleansing-exercise][removeOrphanedChunks] Starting data exercise for removing of chunks")
    for {
      chunks <- chunksRepo.getAllChunks().map(_.map(_.files_id).distinct)

      fileInfoList <- {
        Logger.info(s"[data-cleansing-exercise][removeOrphanedChunks] Size of chunks to verify is: ${chunks.size}" )
        processFutures(chunks)(fileRepo.isFileExists(_))
      }

      chunksDeleted <- {
        val parentFileIds = fileInfoList.filter(_.isDefined).map(rec => rec.get.id.asInstanceOf[BSONObjectID])
        val chunksToBeDeleted = chunks.diff(parentFileIds)
        Logger.info(s"[data-cleansing-exercise][removeOrphanedChunks] Size of fileId's to be deleted is: ${chunksToBeDeleted.length}")

        val res = processFutures(chunksToBeDeleted)(fileId => {
          Logger.warn(s"[data-cleansing-exercise][removeOrphanedChunks] fileId to be deleted is: ${fileId}")
          chunksRepo.removeChunk(fileId).map{
            case true => Logger.info(s"[data-cleansing-exercise][removeOrphanedChunks] Chunk deletion succeeded, fileId is: ${fileId}")
            case false => Logger.warn(s"[data-cleansing-exercise][removeOrphanedChunks] Chunk deletion failed, fileId is: ${fileId}")
          }
        })
        Future(chunksToBeDeleted)
      }
    } yield chunksDeleted
  } else {
    Logger.info("[data-cleansing-exercise][removeOrphanedChunks] No data exercise carried")
    Future.successful(Seq.empty)
  }

  //Further refactor can be done on this
  private def processFutures[A, B](seq: Iterable[A])(fn: A => Future[B]): Future[List[B]] =
    seq.foldLeft(Future(List.empty[B])) {
      (previousFuture, next) =>
        for {
          previousResults <- previousFuture
          next <- fn(next)
        } yield previousResults :+ next
    }

  removeOrphanedChunks()
}
