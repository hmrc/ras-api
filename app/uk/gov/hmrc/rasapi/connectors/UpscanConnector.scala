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

package uk.gov.hmrc.rasapi.connectors

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.{Sink, Source, StreamConverters}
import org.apache.pekko.util.ByteString
import play.api.Logging
import play.api.http.Status.NOT_FOUND
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.rasapi.config.WSHttp
import uk.gov.hmrc.rasapi.models.ConnectorFailure
import uk.gov.hmrc.rasapi.models.FailureReason.{CONNECTION_FAILURE, DOWNLOAD_INCOMPLETE, TIMEOUT, UNEXPECTED_STATUS}
import uk.gov.hmrc.rasapi.services.AuditService
import uk.gov.hmrc.rasapi.utils.CorrelationIdGenerator
import uk.gov.hmrc.rasapi.utils.LogHelper.{failureText, successText}

import java.io.InputStream
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class UpscanConnector @Inject() (
  val wsHttp: WSHttp,
  val auditService: AuditService,
  correlationIdGenerator: CorrelationIdGenerator
)(using ec: ExecutionContext, system: ActorSystem)
    extends Logging {

  private val UPSCAN_DOWNSTREAM_SERVICE: String = "UPSCAN"
  private val UPSCAN_CALL_AUDIT_TYPE: String    = "ReliefAtSourceUpscanFileRetrieval"
  private val OUTCOME_SUCCESS: String           = "SUCCESS"
  private val OUTCOME_FAILURE: String           = "FAILURE"
  private val STREAMING_ERROR: String           = "STREAMING_ERROR"

  private val tag = "[UpscanConnector][getUpscanFile]"

  private def streamingFailure(ex: Throwable): ConnectorFailure =
    ConnectorFailure(DOWNLOAD_INCOMPLETE, cause = Some(ex), detail = Some(STREAMING_ERROR))

  private def requestFailure(ex: Throwable): ConnectorFailure = ex match {
    case _: TimeoutException => ConnectorFailure(TIMEOUT, cause = Some(ex))
    case _                   => ConnectorFailure(CONNECTION_FAILURE, cause = Some(ex))
  }

  def getUpscanFile(downloadUrl: String, reference: String, userId: String)(using
    hc: HeaderCarrier
  ): Future[Option[InputStream]] = {
    val corrId  = correlationIdGenerator.correlationId
    val context = Seq("reference" -> reference, "corrId" -> corrId)

    logger.info(s"$tag Trying to retrieve file from Upscan: $reference for userId ($userId) correlationId ($corrId).")

    def logAndAuditFailure(failure: ConnectorFailure, processingPath: String): Unit = {
      logger.error(failureText(tag, userId, failure, context = context))
      auditUpscanCall(userId, reference, corrId, OUTCOME_FAILURE, Some(failure), processingPath)
    }

    wsHttp
      .buildRequestWithStream(uri = downloadUrl)
      .map { res =>
        val status = res.status

        if (status >= 200 && status < 300) {
          Some(monitoredStream(res.bodyAsSource, userId, reference, corrId))
        } else {
          res.bodyAsSource.runWith(Sink.ignore)

          val failure = ConnectorFailure(UNEXPECTED_STATUS, status = Some(status), detail = Some(s"HTTP_$status"))
          logAndAuditFailure(failure, "UpscanConnector.getUpscanFile.unexpectedStatus")

          if (status == NOT_FOUND) {
            None
          } else {
            throw failure
          }
        }
      }
      .recover {
        case failure: ConnectorFailure =>
          throw failure // already logged and audited above
        case NonFatal(ex)              =>
          val failure = requestFailure(ex)
          logAndAuditFailure(failure, "UpscanConnector.getUpscanFile.recover")
          throw failure
      }
  }

  // package private to test failing body
  private[connectors] def monitoredStream(
    body: Source[ByteString, ?],
    userId: String,
    reference: String,
    corrId: String
  )(using hc: HeaderCarrier): InputStream = {
    val context = Seq("reference" -> reference, "corrId" -> corrId)

    body
      .watchTermination() { (_, done) =>
        done.onComplete {
          case Success(_)  =>
            logger.info(successText(tag, userId, context = context))
            auditUpscanCall(userId, reference, corrId, OUTCOME_SUCCESS, None, "UpscanConnector.getUpscanFile")
          case Failure(ex) =>
            val failure = streamingFailure(ex)
            logger.error(failureText(tag, userId, failure, context = context))

            auditUpscanCall(
              userId,
              reference,
              corrId,
              OUTCOME_FAILURE,
              Some(failure),
              "UpscanConnector.getUpscanFile.streamFailure"
            )
        }
      }
      .runWith(StreamConverters.asInputStream())
  }

  private def auditUpscanCall(
    userId: String,
    reference: String,
    corrId: String,
    outcome: String,
    failure: Option[ConnectorFailure],
    processingPath: String
  )(using hc: HeaderCarrier): Unit = {
    val reason          = failure.map("reason" -> _.reason.toString)
    val httpStatus      = failure.flatMap(_.status).map("httpStatus" -> _.toString)
    val failureCategory = failure.flatMap(_.detail).map("failureCategory" -> _)
    val errorDetail     = failure.flatMap(_.cause).flatMap(c => Option(c.getMessage)).map("errorDetail" -> _)

    val auditData: Map[String, String] = Map(
      "downstreamService" -> UPSCAN_DOWNSTREAM_SERVICE,
      "fileReference"     -> reference,
      "userIdentifier"    -> userId,
      "correlationId"     -> corrId,
      "outcome"           -> outcome,
      "auditEventType"    -> UPSCAN_CALL_AUDIT_TYPE,
      "processingPath"    -> processingPath
    ) ++ reason ++ httpStatus ++ failureCategory ++ errorDetail

    auditService.audit(auditType = UPSCAN_CALL_AUDIT_TYPE, path = reference, auditData = auditData)
  }

}
