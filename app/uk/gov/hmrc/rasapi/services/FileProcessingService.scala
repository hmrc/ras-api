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

package uk.gov.hmrc.rasapi.services

import java.nio.file.Path

import org.joda.time.DateTime
import play.api.Logger
import play.api.mvc.{AnyContent, Request}
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.rasapi.config.AppContext
import uk.gov.hmrc.rasapi.connectors.{DesConnector, FileUploadConnector}
import uk.gov.hmrc.rasapi.helpers.ResidencyYearResolver
import uk.gov.hmrc.rasapi.metrics.Metrics
import uk.gov.hmrc.rasapi.models.{ApiVersion, CallbackData, ResultsFileMetaData, V2_0}
import uk.gov.hmrc.rasapi.repository.{RasFileRepository, RasRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success, Try}

object FileProcessingService extends FileProcessingService {

  override val fileUploadConnector: FileUploadConnector = FileUploadConnector
  override val desConnector: DesConnector = DesConnector
  override val residencyYearResolver: ResidencyYearResolver = ResidencyYearResolver
  override val auditService: AuditService = AuditService
  override val sessionCacheService: SessionCacheService = SessionCacheService
  override val fileRepo: RasFileRepository = RasRepository.filerepo
  override def getCurrentDate: DateTime = DateTime.now()
  override val allowDefaultRUK: Boolean = AppContext.allowDefaultRUK
  override val DECEASED: String = AppContext.deceasedStatus
  override val MATCHING_FAILED: String = AppContext.matchingFailedStatus
  override val INTERNAL_SERVER_ERROR: String = AppContext.internalServerErrorStatus
  override val SERVICE_UNAVAILABLE: String = AppContext.serviceUnavailableStatus
  override val FILE_PROCESSING_MATCHING_FAILED: String = AppContext.fileProcessingMatchingFailedStatus
  override val FILE_PROCESSING_INTERNAL_SERVER_ERROR: String = AppContext.fileProcessingInternalServerErrorStatus
}

trait FileProcessingService extends RasFileReader with RasFileWriter with ResultsGenerator {

  val sessionCacheService: SessionCacheService
  val fileRepo: RasFileRepository

  val fileProcess = "File-Processing"
  val fileRead = "File-Upload-Read"
  val fileResults = "File-Results"
  val fileSave = "File-Save"
  val STATUS_ERROR = "ERROR"

  def processFile(userId: String, callbackData: CallbackData, apiVersion: ApiVersion)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Unit = {
    val fileMetrics = Metrics.register(fileProcess).time
    val fileReadMetrics = Metrics.register(fileRead).time

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

  def manipulateFile(inputFileData: Try[Iterator[String]], userId: String, callbackData: CallbackData, apiVersion: ApiVersion)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Unit = {
    val fileResultsMetrics = Metrics.register(fileResults).time
    val writer = createFileWriter(callbackData.fileId, userId)

    def removeDoubleQuotes(row: String): String = {
      row match {
        case s if s.startsWith("\"") && s.endsWith("\"") => s.drop(1).dropRight(1)
        case _ => row
      }
    }

    try{
      val dataIterator = inputFileData.get.toList
      Logger.warn(s"file data size ${dataIterator.size} for user $userId")
      writeResultToFile(writer._2, s"National Insurance number,First name,Last name,Date of birth,$getTaxYearHeadings", userId)
      dataIterator.foreach(row =>
        if (!row.isEmpty) {
          writeResultToFile(writer._2,fetchResult(removeDoubleQuotes(row),userId,callbackData.fileId, apiVersion = apiVersion), userId)
        }
      )
      closeWriter(writer._2)
      fileResultsMetrics.stop

      Logger.warn(s"File results complete, ready to save the file (fileId: ${callbackData.fileId}) for userId ($userId).")

      saveFile(writer._1, userId, callbackData)

    } catch
      {
        case ex:Throwable => {
          Logger.error(s"error for userId ($userId) in File processing -> ${ex.getMessage}")
          sessionCacheService.updateFileSession(userId, callbackData.copy(status = STATUS_ERROR), None, None)
          fileResultsMetrics.stop
        }
      }
    finally {
      closeWriter(writer._2)
      clearFile( writer._1, userId)
    }
  }

  def saveFile(filePath: Path, userId: String, callbackData: CallbackData)(implicit hc: HeaderCarrier, request: Request[AnyContent]): Unit = {

    Logger.warn("[FileProcessingService] Starting to save file...")

    val fileSaveMetrics = Metrics.register(fileSave).time
    try {
      fileRepo.saveFile(userId, callbackData.envelopeId, filePath, callbackData.fileId).onComplete {
        result =>

          result match {
            case Success(file) =>
              Logger.warn(s"Starting to save the file (${file.id}) for user ID: $userId")

              val resultsFileMetaData = Some(ResultsFileMetaData(file.id.toString, file.filename, file.uploadDate, file.chunkSize, file.length))

              fileUploadConnector.getFileMetadata(callbackData.envelopeId, callbackData.fileId, userId).onComplete {
                case Success(metadata) =>
                  sessionCacheService.updateFileSession(userId, callbackData, resultsFileMetaData, metadata)
                case Failure(ex) =>
                  Logger.error(s"Failed to get File Metadata for file (${file.id}), for user ID: $userId.", ex)
                  sessionCacheService.updateFileSession(userId, callbackData, resultsFileMetaData, None)
              }

              Logger.warn(s"Completed saving the file (${file.id}) for user ID: $userId")

            case Failure(ex) => {
              Logger.error(s"results file for userId ($userId) generation/saving failed with Exception ${ex.getMessage}")
              sessionCacheService.updateFileSession(userId, callbackData.copy(status = STATUS_ERROR), None, None)
            }
            //delete result  a future ind
          }
          fileSaveMetrics.stop
          fileUploadConnector.deleteUploadedFile(callbackData.envelopeId, callbackData.fileId, userId)
      }
    }
  }

  def getTaxYearHeadings = {
    val currentDate = getCurrentDate
    val currentYear = currentDate.getYear
    if (currentDate.isAfter(new DateTime(currentYear - 1, 12, 31, 0, 0, 0, 0)) && currentDate.isBefore(new DateTime(currentYear, 4, 6, 0, 0, 0, 0)))
      s"${currentYear - 1} to $currentYear residency status,$currentYear to ${currentYear + 1} residency status"
    else
      s"$currentYear to ${currentYear + 1} residency status"
  }
}


