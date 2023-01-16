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

import org.joda.time.DateTime
import play.api.Logging
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.connectors.{DesConnector, FileUploadConnector}
import uk.gov.hmrc.rasapi.helpers.ResidencyYearResolver
import uk.gov.hmrc.rasapi.metrics.Metrics
import uk.gov.hmrc.rasapi.models.{ApiVersion, CallbackData, ResultsFileMetaData}
import uk.gov.hmrc.rasapi.repository.RasFilesRepository

import java.nio.file.Path
import javax.inject.Inject
import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

class FileProcessingService @Inject()(
                                     val fileUploadConnector: FileUploadConnector,
                                     val desConnector: DesConnector,
                                     val residencyYearResolver: ResidencyYearResolver,
                                     val auditService: AuditService,
                                     val sessionCacheService: SessionCacheService,
                                     val fileRepo: RasFilesRepository,
                                     val appContext: AppContext,
                                     val metrics: Metrics,
                                     implicit val ec: ExecutionContext
                                     ) extends RasFileReader with RasFileWriter with ResultsGenerator with Logging {

  def getCurrentDate: DateTime = DateTime.now()

  val allowDefaultRUK: Boolean = appContext.allowDefaultRUK
  val DECEASED: String = appContext.deceasedStatus
  val MATCHING_FAILED: String = appContext.matchingFailedStatus
  val INTERNAL_SERVER_ERROR: String = appContext.internalServerErrorStatus
  val SERVICE_UNAVAILABLE: String = appContext.serviceUnavailableStatus
  val FILE_PROCESSING_MATCHING_FAILED: String = appContext.fileProcessingMatchingFailedStatus
  val FILE_PROCESSING_INTERNAL_SERVER_ERROR: String = appContext.fileProcessingInternalServerErrorStatus

  val fileProcess: String = "File-Processing"
  val fileRead: String = "File-Upload-Read"
  val fileResults: String = "File-Results"
  val fileSave: String = "File-Save"
  val STATUS_ERROR: String = "ERROR"

  def processFile(userId: String, callbackData: CallbackData, apiVersion: ApiVersion)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Unit = {
    val fileMetrics = metrics.register(fileProcess).time
    val fileReadMetrics = metrics.register(fileRead).time

    readFile(callbackData.envelopeId, callbackData.fileId, userId).onComplete {
      fileReadMetrics.stop
      inputFileData =>
        if(inputFileData.isSuccess)
        {
          manipulateFile(inputFileData, userId, callbackData, apiVersion)
        }
    }
    fileMetrics.stop
  }

  def manipulateFile(inputFileData: Try[Iterator[String]],
                     userId: String,
                     callbackData: CallbackData,
                     apiVersion: ApiVersion
                    )(implicit hc: HeaderCarrier, request: Request[AnyContent]): Unit = {
    val fileResultsMetrics = metrics.register(fileResults).time
    val writer = createFileWriter(callbackData.fileId, userId)

    def removeDoubleQuotes(row: String): String = {
      row match {
        case s if s.startsWith("\"") && s.endsWith("\"") => s.drop(1).dropRight(1)
        case _ => row
      }
    }

    try{
      val dataIterator = inputFileData.get.toList
      logger.info(s"[FileProcessingService][manipulateFile] File data size ${dataIterator.size} for user $userId")
      writeResultToFile(writer._2, s"National Insurance number,First name,Last name,Date of birth,$getTaxYearHeadings", userId)
      dataIterator.foreach(row =>
        if (row.nonEmpty) {
          writeResultToFile(writer._2,fetchResult(removeDoubleQuotes(row),userId,callbackData.fileId, apiVersion = apiVersion), userId)
        }
      )
      closeWriter(writer._2)
      fileResultsMetrics.stop

      logger.info(s"[FileProcessingService][manipulateFile] File results complete, ready to save the file (fileId: ${callbackData.fileId}) for userId ($userId).")

      saveFile(writer._1, userId, callbackData)

    } catch
      {
        case ex:Throwable =>
          logger.error(s"[FileProcessingService][manipulateFile] Error for userId ($userId) in File processing -> ${ex.getMessage}", ex)
          sessionCacheService.updateFileSession(userId, callbackData.copy(status = STATUS_ERROR), None, None)
          fileResultsMetrics.stop
      }
    finally {
      closeWriter(writer._2)
      clearFile( writer._1, userId)
    }
  }

  def saveFile(filePath: Path, userId: String, callbackData: CallbackData)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Unit = {

    logger.info("[FileProcessingService][saveFile] Starting to save file...")
    val fileSaveMetrics = metrics.register(fileSave).time
      fileRepo.saveFile(userId, callbackData.envelopeId, filePath, callbackData.fileId).onComplete {
        result =>
          result match {
            case Success(file) =>
              logger.info(s"[FileProcessingService][saveFile] Starting to save the file (${file.getId}) for user ID: $userId")

              val resultsFileMetaData = Some(ResultsFileMetaData(file.getId.toString, file.getFilename, file.getUploadDate.getTime, file.getChunkSize, file.getLength))

              fileUploadConnector.getFileMetadata(callbackData.envelopeId, callbackData.fileId, userId).onComplete {
                case Success(metadata) =>
                  sessionCacheService.updateFileSession(userId, callbackData, resultsFileMetaData, metadata)
                case Failure(ex) =>
                  logger.error(s"[FileProcessingService][saveFile] Failed to get File Metadata for file (${file.getId}), for user ID: $userId, message: ${ex.getMessage}", ex)
                  sessionCacheService.updateFileSession(userId, callbackData, resultsFileMetaData, None)
              }
              logger.info(s"[FileProcessingService][saveFile] Completed saving the file (${file.getId}) for user ID: $userId")
            case Failure(ex) =>
              logger.error(s"[FileProcessingService][saveFile] results file for userId ($userId) generation/saving failed with Exception ${ex.getMessage}", ex)
              sessionCacheService.updateFileSession(userId, callbackData.copy(status = STATUS_ERROR), None, None)
            //delete result  a future ind
          }
          fileSaveMetrics.stop
          fileUploadConnector.deleteUploadedFile(callbackData.envelopeId, callbackData.fileId, userId)
      }
  }

  def getTaxYearHeadings: String = {
    val currentDate = getCurrentDate
    val currentYear = currentDate.getYear
    if (currentDate.isAfter(new DateTime(currentYear - 1, 12, 31, 0, 0, 0, 0)) && currentDate.isBefore(new DateTime(currentYear, 4, 6, 0, 0, 0, 0)))
      s"${currentYear - 1} to $currentYear residency status,$currentYear to ${currentYear + 1} residency status"
    else
      s"$currentYear to ${currentYear + 1} residency status"
  }
}
