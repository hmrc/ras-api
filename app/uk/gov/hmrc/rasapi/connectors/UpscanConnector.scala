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
import org.apache.pekko.stream.scaladsl.{Sink, StreamConverters}
import play.api.Logging
import play.api.http.Status.NOT_FOUND
import play.api.libs.ws.WSResponse
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.rasapi.config.WSHttp
import uk.gov.hmrc.rasapi.models.ConnectorFailure
import uk.gov.hmrc.rasapi.models.FailureReason.{DOWNLOAD_INCOMPLETE, UNEXPECTED_STATUS}
import uk.gov.hmrc.rasapi.services.AuditService
import uk.gov.hmrc.rasapi.utils.CorrelationIdGenerator
import uk.gov.hmrc.rasapi.utils.LogHelper.{failureText, successText}

import java.io.{FilterInputStream, InputStream}
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.{NoStackTrace, NonFatal}

/**
 * Connector responsible for retrieving files from the downstream Upscan service.
 * Retrieves file content as a streaming InputStream rather than loading the entire
 * file into memory. Monitors the consumption of the stream to distinguish between
 * successful downloads, streaming failures and downloads closed before reaching EOF.
 * Logs and audits each retrieval outcome with user, file reference and correlation
 * information to support end-to-end traceability in Splunk.
 */

class UpscanConnector @Inject() (
  val wsHttp: WSHttp,
  val auditService: AuditService,
  correlationIdGenerator: CorrelationIdGenerator
)(using ec: ExecutionContext, system: ActorSystem)
    extends Logging {

  // Constants used to identify Upscan retrieval audit events and classify download outcomes.
  private val UPSCAN_DOWNSTREAM_SERVICE: String = "UPSCAN"
  private val UPSCAN_CALL_AUDIT_TYPE: String    = "ReliefAtSourceUpscanFileRetrieval"
  private val OUTCOME_SUCCESS: String           = "SUCCESS"
  private val OUTCOME_FAILURE: String           = "FAILURE"
  private val STREAMING_ERROR: String           = "STREAMING_ERROR"
  private val CLOSED_BEFORE_EOF: String         = "CLOSED_BEFORE_EOF"

  private val tag = "[UpscanConnector][getUpscanFile]"

  private def streamingFailure(ex: Throwable): ConnectorFailure =
    ConnectorFailure(DOWNLOAD_INCOMPLETE, cause = Some(ex), detail = Some(STREAMING_ERROR))

  // Identifies non-2xx HTTP responses that have already been logged and audited.
  // Allows recover to propagate these failures without logging or auditing them again.
  private class UnexpectedStatusException(message: String) extends RuntimeException(message) with NoStackTrace

  /**
   *
   * Retrieves a file from Upscan using a streaming HTTP request.
   * Returns Some(InputStream) for a successful HTTP response, None for HTTP 404,
   * or a failed Future for other unexpected responses or request failures.
   * The returned InputStream is monitored separately so that the actual download
   * outcome is logged and audited when the caller consumes or closes the stream.
   */

  def getUpscanFile(downloadUrl: String, reference: String, userId: String)(using
    hc: HeaderCarrier
  ): Future[Option[InputStream]] = {
    // Generate a correlation ID to link logs and audit events for this retrieval.
    val corrId = correlationIdGenerator.correlationId
    logger.info(
      s"[UpscanConnector][getUpscanFile] Trying to retrieve file from Upscan: $reference for userId ($userId) correlationId ($corrId)."
    )

    val context = Seq("reference" -> reference, "corrId" -> corrId)

    def logFailure(failure: ConnectorFailure): ConnectorFailure = {
      logger.error(failureText(tag, userId, failure, context = context), failure.cause.orNull)
      failure
    }

    // Process the HTTP response: return a monitored stream for 2xx responses,
    // treat 404 as a missing file, and propagate other non-2xx responses as failures.

    wsHttp.buildRequestWithStream(uri = downloadUrl).map { res =>
      val status = res.status

      if (status >= 200 && status < 300) {
        Some(monitoredStream(res, userId, reference, corrId))
      } else {
        // A non-2xx response still resolves successfully from `.stream()`; discard the error body so the
        // underlying connection is released rather than streamed to the caller as if it were the file.
        res.bodyAsSource.runWith(Sink.ignore)

        val failure =
          logFailure(ConnectorFailure(UNEXPECTED_STATUS, status = Some(status), detail = Some(s"HTTP_$status")))
        auditUpscanCall(
          userId,
          reference,
          corrId,
          outcome = OUTCOME_FAILURE,
          failure = Some(failure),
          processingPath = "UpscanConnector.getUpscanFile.unexpectedStatus"
        )

        // 404 is a genuine "file not found"; the caller turns None into a FileNotFoundException. Any other
        // non-2xx is a hard failure the caller cannot recover from, so surface it as a failed Future.
        if (status == NOT_FOUND) None
        else
          throw new UnexpectedStatusException(
            s"Unexpected status $status retrieving file from Upscan service (corrId=$corrId)"
          )
      }
      // Handle request-level failures. UnexpectedStatusException is excluded because
      // non-2xx responses have already been logged and audited above
      // Already handled in `.map`; propagate without re-logging or re-auditing.
    } recover {
      case ex: UnexpectedStatusException => throw ex
      case NonFatal(ex)                  =>
        val failure = logFailure(streamingFailure(ex))
        auditUpscanCall(
          userId,
          reference,
          corrId,
          outcome = OUTCOME_FAILURE,
          failure = Some(failure),
          processingPath = "UpscanConnector.getUpscanFile.recover"
        )
        throw new RuntimeException(s"Error Streaming file from Upscan service (corrId=$corrId)", ex)
    }
  }

  /**
   * Wraps the Upscan response body in a monitored InputStream to track the actual
   * consumption of the downloaded file.
   * Records exactly one terminal outcome per retrieval:
   * ￼	SUCCESS: EOF reached and the complete stream has been consumed.
   * ￼	FAILURE: An exception occurs while reading or closing the stream.
   * ￼	FAILURE: The caller closes the stream before reaching EOF.
   * Tracks the number of bytes read and uses an AtomicBoolean to prevent duplicate
   * logging and auditing when multiple terminal events occur.
   */
  private def monitoredStream(
    res: WSResponse,
    userId: String,
    reference: String,
    corrId: String
  )(using hc: HeaderCarrier): InputStream = {
    // Convert the Pekko response stream into a Java InputStream for the caller to consume.
    val inputStream     = res.bodyAsSource.runWith(StreamConverters.asInputStream())
    val context         = Seq("reference" -> reference, "corrId" -> corrId)
    // Track whether a terminal outcome has already been recorded and the total
    // number of bytes consumed. AtomicBoolean prevents duplicate audit events.
    val audited         = new AtomicBoolean(false)
    var bytesRead: Long = 0L

    // Record a successful retrieval when EOF is reached, including the total bytes read.
    def auditSuccess(): Unit =
      if (audited.compareAndSet(false, true)) {
        logger.info(successText(tag, userId, context = context :+ ("bytesRead" -> bytesRead.toString)))
        auditUpscanCall(
          userId,
          reference,
          corrId,
          outcome = OUTCOME_SUCCESS,
          failure = None,
          processingPath = "UpscanConnector.getUpscanFile",
          bytesRead = Some(bytesRead)
        )
      }

    // Record a failed retrieval, including the failure details and the stage at which it occurred.
    def auditFailure(failure: ConnectorFailure, processingPath: String): Unit =
      if (audited.compareAndSet(false, true)) {
        logger.error(failureText(tag, userId, failure, context = context), failure.cause.orNull)
        auditUpscanCall(
          userId,
          reference,
          corrId,
          outcome = OUTCOME_FAILURE,
          failure = Some(failure),
          processingPath = processingPath,
          bytesRead = Some(bytesRead)
        )
      }

    // Decorate the underlying stream to intercept reads and close operations,
    // allowing the actual download outcome to be monitored without changing
    // how the caller consumes the InputStream.
    new FilterInputStream(inputStream) {
      // Shared read handling for both InputStream read methods.
      // Captures streaming exceptions, detects EOF and updates the byte count.
      private def monitored(read: => Int, byteCount: Int => Long): Int = {
        val result =
          try read
          catch {
            case NonFatal(ex) =>
              auditFailure(streamingFailure(ex), "UpscanConnector.getUpscanFile.streamFailure")
              throw ex
          }
        if (result == -1) auditSuccess()
        else bytesRead += byteCount(result)
        result
      }

      // Intercept both single-byte and buffered reads so all consumed bytes are counted.
      override def read(): Int = monitored(super.read(), _ => 1L)

      override def read(b: Array[Byte], off: Int, len: Int): Int = monitored(super.read(b, off, len), _.toLong)

      // Close the underlying stream and detect whether the caller abandoned the
      // download before EOF. Only record an incomplete download if no terminal
      // outcome has already been recorded.
      override def close(): Unit = {
        try super.close()
        catch {
          case NonFatal(ex) =>
            auditFailure(streamingFailure(ex), "UpscanConnector.getUpscanFile.closeFailure")
            throw ex
        }
        // Reaching close() without a prior terminal outcome means the caller stopped reading before EOF.
        if (audited.compareAndSet(false, true)) {
          val failure = ConnectorFailure(DOWNLOAD_INCOMPLETE, detail = Some(CLOSED_BEFORE_EOF))
          logger.warn(failureText(tag, userId, failure, context = context :+ ("bytesRead" -> bytesRead.toString)))
          auditUpscanCall(
            userId,
            reference,
            corrId,
            outcome = OUTCOME_FAILURE,
            failure = Some(failure),
            processingPath = "UpscanConnector.getUpscanFile.incomplete",
            bytesRead = Some(bytesRead)
          )
        }
      }
    }
  }

  /**
   *
   * Builds and emits an enriched audit event for an Upscan file retrieval.
   * Includes the downstream service, file reference, user identifier, correlation ID,
   * outcome and processing path. Where applicable, also includes bytes read and
   * structured failure details such as the failure reason, HTTP status and error message.
   * Centralises audit construction to ensure consistent audit data across all
   * retrieval success and failure scenarios.
   */
  private def auditUpscanCall(
    userId: String,
    reference: String,
    corrId: String,
    outcome: String,
    failure: Option[ConnectorFailure],
    processingPath: String,
    bytesRead: Option[Long] = None
  )(using hc: HeaderCarrier): Unit = {

    val reason          = failure.map("reason" -> _.reason.toString)
    val httpStatus      = failure.flatMap(_.status).map("httpStatus" -> _.toString)
    val failureCategory = failure.flatMap(_.detail).map("failureCategory" -> _)
    val errorDetail     = failure.flatMap(_.cause).flatMap(c => Option(c.getMessage)).map("errorDetail" -> _)
    val bytes           = bytesRead.map("bytesRead" -> _.toString)

    // Combine mandatory retrieval context with any available failure and download details.
    val auditData: Map[String, String] = Map(
      "downstreamService" -> UPSCAN_DOWNSTREAM_SERVICE,
      "fileReference"     -> reference,
      "userIdentifier"    -> userId,
      "correlationId"     -> corrId,
      "outcome"           -> outcome,
      "auditEventType"    -> UPSCAN_CALL_AUDIT_TYPE,
      "processingPath"    -> processingPath
    ) ++ reason ++ httpStatus ++ failureCategory ++ errorDetail ++ bytes

    auditService.audit(
      auditType = UPSCAN_CALL_AUDIT_TYPE,
      path = reference,
      auditData = auditData
    )
  }

}
