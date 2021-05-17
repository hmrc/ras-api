/*
 * Copyright 2021 HM Revenue & Customs
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
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.http.cache.client.{CacheMap, ShortLivedHttpCaching}
import uk.gov.hmrc.rasapi.models.{CallbackData, FileMetadata, FileSession, ResultsFileMetaData}

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class SessionCacheService @Inject()(val sessionCache: ShortLivedHttpCaching,
                                    implicit val ec: ExecutionContext
                                   ) extends Logging {
  private val source: String = "ras"
  private val formId: String = "fileSession"

  def updateFileSession(userId: String,
                        userFile: CallbackData,
                        resultsFile: Option[ResultsFileMetaData],
                        fileMetadata: Option[FileMetadata]
                       )(implicit hc: HeaderCarrier): Future[CacheMap] = {

    sessionCache.fetchAndGetEntry[FileSession](source,userId,formId).flatMap{ session =>
    sessionCache.cache[FileSession](source,userId,formId,
      FileSession(Some(userFile), resultsFile, userId, session.get.uploadTimeStamp, fileMetadata) ).recover {
        case ex: Throwable => logger.error(s"[SessionCacheService][updateFileSession] unable to save FileSession to cache => " +
          s"userId ($userId) , userFile : ${userFile.toString} , resultsFile id : " +
          s"${if(resultsFile.isDefined) resultsFile.get.id}, \n Exception is ${ex.getMessage}", ex)
          throw new RuntimeException("Error in saving sessionCache" + ex.getMessage)
      }
    }.recover {
      case ex: Throwable => logger.error(s"[SessionCacheService][updateFileSession] cannot fetch  data to cache for FileSession => " +
        s"userId ($userId) , userFile : ${userFile.toString} , resultsFile id : " +
        s"${if(resultsFile.isDefined) resultsFile.get.id}, \n Exception is ${ex.getMessage}", ex)
        throw new RuntimeException(s"Error in saving sessionCache ${ex.getMessage}")
    }
  }
}
