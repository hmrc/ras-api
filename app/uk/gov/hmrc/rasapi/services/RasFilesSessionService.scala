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

package uk.gov.hmrc.rasapi.services

import play.api.Logging
import uk.gov.hmrc.mongo.cache.{CacheItem, DataKey}
import uk.gov.hmrc.rasapi.models.{CallbackData, FileMetadata, FileSession, ResultsFileMetaData}
import uk.gov.hmrc.rasapi.repository.RasFilesSessionRepository

import javax.inject.{Inject, Singleton}
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class RasFilesSessionService @Inject()(filesSessionRepository: RasFilesSessionRepository)
                                      (implicit ec: ExecutionContext) extends Logging {

  def updateFileSession(userId: String, userFile: CallbackData, resultsFile: Option[ResultsFileMetaData],
                        fileMetadata: Option[FileMetadata]): Future[CacheItem] = {

    (for {
      session <- filesSessionRepository.get[FileSession](userId)(DataKey("fileSession"))
      data = FileSession(Some(userFile), resultsFile, userId, session.flatMap(_.uploadTimeStamp), fileMetadata)
      cacheItem <- filesSessionRepository.put[FileSession](userId)(DataKey("fileSession"), data)
    } yield {
      logger.info(s"Updated file session repository for user: ${cacheItem.id}")
      cacheItem
    }).recover {
      case ex: Throwable => logger.error(s"[SessionCacheService][updateFileSession] unable to update FileSession => " +
        s"userId ($userId) , userFile : ${userFile.toString} , resultsFile id : " +
        s"${if (resultsFile.isDefined) resultsFile.get.id}, \n Exception is ${ex.getMessage}", ex)
        throw new RuntimeException("Error in saving sessionCache" + ex.getMessage)
    }
  }
}
