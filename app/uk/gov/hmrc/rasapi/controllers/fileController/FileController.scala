/*
 * Copyright 2026 HM Revenue & Customs
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

package uk.gov.hmrc.rasapi.controllers.fileController

import org.apache.pekko.stream.scaladsl.Source
import org.apache.pekko.util.ByteString
import org.bson.types.ObjectId
import play.api.Logging
import play.api.http.HttpEntity
import play.api.mvc._
import uk.gov.hmrc.api.controllers.{ErrorInternalServerError => ErrorInternalServerErrorHmrc, ErrorResponse => ErrorResponseHmrc}
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.v2.Retrievals.authorisedEnrolments
import uk.gov.hmrc.play.bootstrap.backend.controller.BackendController
import uk.gov.hmrc.rasapi.controllers.{ErrorNotFound, InvalidCredentials, PP_ENROLMENT, PSA_ENROLMENT, PSA_PODS_ENROLMENT, PSP_ENROLMENT, Unauthorised, errorResponseWrites, getEnrolmentIdentifier}
import uk.gov.hmrc.rasapi.metrics.Metrics
import uk.gov.hmrc.rasapi.repository.{FileData, RasChunksRepository, RasFilesRepository}
import uk.gov.hmrc.rasapi.services.AuditService

import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class FileController @Inject()(
                                fileRepo: RasFilesRepository,
                                chunksRepo: RasChunksRepository,
                                metrics: Metrics,
                                val auditService: AuditService,
                                val authConnector: AuthConnector,
                                cc: ControllerComponents,
                                implicit val ec: ExecutionContext
                              ) extends BackendController(cc) with AuthorisedFunctions with Logging {

  val fileRemove: String = "File-Remove"
  val fileServe: String = "File-Read"
  private val _contentType: String = "application/csv"

  def parseStringIdToObjectId(id: String): Try[ObjectId] = {
    Try {
      logger.info(s"[FileController][parseStringIdToObjectId] Attempting to convert string id $id into an ObjectId")
      new ObjectId(id)
    }
  }

  def serveFile(fileName:String): Action[AnyContent] = Action.async {
    implicit request =>
      val apiMetrics = metrics.register(fileServe).time
      authorised(AuthProviders(GovernmentGateway) and (Enrolment(PSA_ENROLMENT) or Enrolment(PP_ENROLMENT) or Enrolment(PSA_PODS_ENROLMENT) or Enrolment(PSP_ENROLMENT))).retrieve(authorisedEnrolments) {
        enrols =>
          val id = getEnrolmentIdentifier(enrols)
          getFile(fileName, id).map { fileData =>
            if (fileData.isDefined) {
              logger.debug("[FileController][serverFile] File repo enumerator received")
              val bodySource: Source[ByteString, _] = fileData.get.data.map(ByteString.fromArray)
              apiMetrics.stop()
              Ok.sendEntity(HttpEntity.Streamed(bodySource, Some(fileData.get.length), Some(_contentType)))
                .withHeaders(CONTENT_DISPOSITION -> s"""attachment; filename="$fileName"""",
                  CONTENT_LENGTH -> s"${fileData.get.length}",
                  CONTENT_TYPE -> _contentType)
            }
            else {
              logger.error(s"[FileController][serverFile] Requested File not found to serve fileName is $fileName")
              NotFound(errorResponseWrites.writes(ErrorNotFound))
            }

          }.recover {
            case ex: Throwable =>
              logger.error(s"[FileController][serverFile] Request failed with Exception ${ex.getMessage} for userId ($id) file -> $fileName")
              InternalServerError
          }
      } recoverWith{
        handleAuthFailure
          }
  }

  def remove(fileName: String, userId: String):  Action[AnyContent] = Action.async {
    implicit request =>
      val apiMetrics = metrics.register(fileRemove).time
      authorised(AuthProviders(GovernmentGateway) and (Enrolment(PSA_ENROLMENT) or Enrolment(PP_ENROLMENT) or Enrolment(PSA_PODS_ENROLMENT) or Enrolment(PSP_ENROLMENT))).retrieve(authorisedEnrolments) {
        enrols =>
          val id = getEnrolmentIdentifier(enrols)
          deleteFile(fileName, id).flatMap { res =>
            (parseStringIdToObjectId(fileName) match {
              case Success(objectId) => chunksRepo.removeChunk(objectId).map { isChunkRemoved =>
                if (isChunkRemoved) {
                  logger.info(s"[FileController][remove] Chunk deletion succeeded, fileName is: $fileName")
                  auditService.audit(auditType = "FileDeletion",
                    path = request.path,
                    auditData = Map("userIdentifier" -> id, "fileName" -> fileName, "chunkDeletionSuccess" -> "true")
                  )
                } else {
                  auditService.audit(auditType = "FileDeletion",
                    path = request.path,
                    auditData = Map("userIdentifier" -> id, "fileName" -> fileName, "chunkDeletionSuccess" -> "false")
                  )
                  logger.warn(s"[FileController][remove] Chunk deletion failed, fileName is: $fileName")
                }
              }.recover {
                case ex: Throwable =>
                  logger.error(s"[FileController][remove] Exception ${ex.getMessage} was thrown", ex)
              }.map(_ => ())
              case Failure(ex) =>
                logger.warn(s"[FileController][remove] Exception ${ex.getMessage} was thrown," +
                  s" the following file ($fileName) and userID $id could not be converted to a ObjectId.")

                auditService.audit(auditType = "FileDeletion", path = request.path,
                  auditData = Map("userIdentifier" -> id,
                    "fileName" -> fileName,
                    "chunkDeletionSuccess" -> "false",
                    "reason" -> "fileName could not be converted to ObjectId"
                  )
                )
                Future.successful(())
            }).map {
              _ =>
                apiMetrics.stop()
                if(res) Ok("") else InternalServerError
            }
          }.recover {
            case ex: Throwable => logger.error(s"[FileController][remove] Request failed with Exception ${ex.getMessage} for userId ($id) file -> $fileName")
              InternalServerError
          }
      } recoverWith{
        handleAuthFailure
      }
  }

  private def handleAuthFailure: PartialFunction[Throwable, Future[Result]] = {
      case _: InsufficientEnrolments => logger.warn("[FileController][handleAuthFailure] Insufficient privileges")
        metrics.registry.counter(UNAUTHORIZED.toString)
        Future.successful(Unauthorized(errorResponseWrites.writes(Unauthorised)))
      case _: NoActiveSession => logger.warn("[FileController][handleAuthFailure] Inactive session")
        metrics.registry.counter(UNAUTHORIZED.toString)
        Future.successful(Unauthorized(errorResponseWrites.writes(InvalidCredentials)))
      case ex => logger.error(s"[FileController][handleAuthFailure] Exception ${ex.getMessage} was thrown from auth", ex)
        Future.successful(InternalServerError(ErrorResponseHmrc.writes.writes(ErrorInternalServerErrorHmrc)))
  }

  // $COVERAGE-OFF$Trivial and never going to be called by a test that uses it's own object implementation

  def getFile(name: String, userId: String): Future[Option[FileData]] = fileRepo.fetchFile(name, userId)

  def deleteFile(name: String, userId: String): Future[Boolean] = fileRepo.removeFile(name, userId)
  // $COVERAGE-ON$


}
