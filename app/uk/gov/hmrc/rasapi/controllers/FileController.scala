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

package uk.gov.hmrc.rasapi.controllers

import akka.stream.scaladsl.Source
import akka.util.ByteString
import javax.inject.Inject
import play.api.Logger
import play.api.http.HttpEntity
import play.api.libs.json.Json.toJson
import play.api.libs.streams.Streams
import play.api.mvc.{Action, AnyContent, Request, Result}
import reactivemongo.bson.BSONObjectID
import uk.gov.hmrc.auth.core.AuthProvider.GovernmentGateway
import uk.gov.hmrc.auth.core._
import uk.gov.hmrc.auth.core.retrieve.Retrievals.authorisedEnrolments
import uk.gov.hmrc.play.bootstrap.controller.BaseController
import uk.gov.hmrc.rasapi.metrics.Metrics
import uk.gov.hmrc.rasapi.repository.{FileData, RasChunksRepository, RasFilesRepository}
import uk.gov.hmrc.rasapi.services.AuditService

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

class FileController @Inject()(
                                fileRepo: RasFilesRepository,
                                chunksRepo: RasChunksRepository,
                                metrics: Metrics,
                                val auditService: AuditService,
                                val authConnector: AuthConnector,
                                implicit val ec: ExecutionContext
                              ) extends BaseController with AuthorisedFunctions {

  val fileRemove: String = "File-Remove"
  val fileServe: String = "File-Read"
  private val _contentType: String = "application/csv"

  def parseStringIdToBSONObjectId(id: String): Try[BSONObjectID] = BSONObjectID.parse(id)

  def serveFile(fileName:String):  Action[AnyContent] = Action.async {
    implicit request =>
      val apiMetrics = metrics.register(fileServe).time
      authorised(AuthProviders(GovernmentGateway) and (Enrolment(PSA_ENROLMENT) or Enrolment(PP_ENROLMENT))).retrieve(authorisedEnrolments) {
        enrols =>
          val id = getEnrolmentIdentifier(enrols)
          getFile(fileName, id).map { fileData =>
            if (fileData.isDefined) {
              Logger.debug("[FileController][serverFile] File repo enumerator received")
              val byteArray = Source.fromPublisher(Streams.enumeratorToPublisher(fileData.get.data.map(ByteString.fromArray)))
              apiMetrics.stop()
              Ok.sendEntity(HttpEntity.Streamed(byteArray, Some(fileData.get.length), Some(_contentType)))
                .withHeaders(CONTENT_DISPOSITION -> s"""attachment; filename="${fileName}"""",
                  CONTENT_LENGTH -> s"${fileData.get.length}",
                  CONTENT_TYPE -> _contentType)
            }
            else {
              Logger.error(s"[FileController][serverFile] Requested File not found to serve fileName is $fileName")
              NotFound(toJson(ErrorNotFound))
            }

          }.recover {
            case ex: Throwable =>
              Logger.error(s"[FileController][serverFile] Request failed with Exception ${ex.getMessage} for userId ($id) file -> $fileName")
              InternalServerError
          }
      } recoverWith{
        handleAuthFailure
          }
  }

  def remove(fileName:String, fileId:String):  Action[AnyContent] = Action.async {
    implicit request =>
      val apiMetrics = metrics.register(fileRemove).time
      authorised(AuthProviders(GovernmentGateway) and (Enrolment(PSA_ENROLMENT) or Enrolment(PP_ENROLMENT))).retrieve(authorisedEnrolments) {
        enrols =>
          val id = getEnrolmentIdentifier(enrols)
          deleteFile(fileName, fileId:String, id).flatMap { res=>
            (parseStringIdToBSONObjectId(fileId) match {
              case Success(bsonId) => chunksRepo.removeChunk(bsonId).map { isChunkRemoved =>
                if (isChunkRemoved) {
                  Logger.info(s"[FileController][remove] Chunk deletion succeeded, fileId is: ${fileId}")
                  auditService.audit(auditType = "FileDeletion",
                    path = request.path,
                    auditData = Map("userIdentifier" -> id, "fileId" -> fileId, "chunkDeletionSuccess" -> "true")
                  )
                } else {
                  auditService.audit(auditType = "FileDeletion",
                    path = request.path,
                    auditData = Map("userIdentifier" -> id, "fileId" -> fileId, "chunkDeletionSuccess" -> "false")
                  )
                  Logger.warn(s"[FileController][remove] Chunk deletion failed, fileId is: ${fileId}")
                }
              }.recover {
                case ex: Throwable => {
                  Logger.error(s"[FileController][remove] Exception ${ex.getMessage} was thrown", ex)
                }
              }.map(_ => ())
              case Failure(ex) => {
                Logger.warn(s"[FileController][remove] Exception ${ex.getMessage} was thrown, the following fileId ($fileId) could not be converted to a BSONObjectId.")
                auditService.audit(auditType = "FileDeletion",
                  path = request.path,
                  auditData = Map("userIdentifier" -> id, "fileId" -> fileId, "chunkDeletionSuccess" -> "false",
                    "reason" -> "fileId could not be converted to BSONObjectId")
                )
                Future.successful(())
              }
            }).map {
              _ =>
                apiMetrics.stop()
                if(res) Ok("") else InternalServerError
            }
          }.recover {
            case ex: Throwable => Logger.error(s"[FileController][remove] Request failed with Exception ${ex.getMessage} for userId ($id) file -> $fileName")
              InternalServerError
          }
      } recoverWith{
        handleAuthFailure
      }
  }

  private def handleAuthFailure(implicit request: Request[_]): PartialFunction[Throwable, Future[Result]] =
    PartialFunction[Throwable, Future[Result]] {
      case _:InsufficientEnrolments => Logger.warn("[FileController][handleAuthFailure] Insufficient privileges")
        metrics.registry.counter(UNAUTHORIZED.toString)

        Future.successful(Unauthorized(toJson(Unauthorised)))

      case _: NoActiveSession => Logger.warn("[FileController][handleAuthFailure] Inactive session")
        metrics.registry.counter(UNAUTHORIZED.toString)
        Future.successful(Unauthorized(toJson(InvalidCredentials)))
      case ex => Logger.error(s"[FileController][handleAuthFailure] Exception ${ex.getMessage} was thrown from auth", ex)
        Future.successful(InternalServerError(toJson(ErrorInternalServerError)))
  }

  // $COVERAGE-OFF$Trivial and never going to be called by a test that uses it's own object implementation

  def getFile(name:String, userId: String): Future[Option[FileData]] = fileRepo.fetchFile(name, userId)

  //TODO: name and fileid are the same so might want to refactor it
  def deleteFile(name:String, fileId:String, userId: String):Future[Boolean] = fileRepo.removeFile(name,fileId,userId)
  // $COVERAGE-ON$


}
