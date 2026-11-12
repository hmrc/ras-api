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
import org.apache.pekko.stream.scaladsl.StreamConverters
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.rasapi.config.{AppContext, WSHttp}
import uk.gov.hmrc.rasapi.services.AuditService
import uk.gov.hmrc.rasapi.utils.CorrelationIdGenerator

import java.io.InputStream
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UpscanConnector @Inject() (
  val wsHttp: WSHttp,
  val auditService: AuditService,
  val appContext: AppContext,
  correlationIdGenerator: CorrelationIdGenerator
)(using ec: ExecutionContext)
    extends Logging {

  val UPSCAN_DOWNSTREAM_SERVICE: String = "UPSCAN"
  val UPSCAN_CALL_AUDIT_TYPE: String    = "ReliefAtSourceUpscanFileRetrieval"
  val OUTCOME_SUCCESS: String           = "SUCCESS"
  val OUTCOME_FAILURE: String           = "FAILURE"

  def getUpscanFile(downloadUrl: String, reference: String, userId: String)(using
    hc: HeaderCarrier
  ): Future[Option[InputStream]] = {
    given system: ActorSystem = ActorSystem()

    val corrId = correlationIdGenerator.correlationId

    logger.info(
      s"[UpscanConnector][getUpscanFile] Trying to retrieve file from Upscan: $reference for userId ($userId) correlationId ($corrId)."
    )

    wsHttp.buildRequestWithStream(uri = downloadUrl).map { res =>
      auditUpscanCall(
        userId,
        reference,
        corrId,
        outcome = OUTCOME_SUCCESS,
        failureCategory = None,
        errorDetail = None,
        processingPath = "UpscanConnector.getUpscanFile"
      )
      Some(res.bodyAsSource.runWith(StreamConverters.asInputStream()))
    } recover { case ex: Throwable =>
      logger.error(
        s"[UpscanConnector][getUpscanFile] Exception thrown while retrieving file / converting to InputStream for userId ($userId) correlationId ($corrId). ${ex.getMessage}}.",
        ex
      )
      auditUpscanCall(
        userId,
        reference,
        corrId,
        outcome = OUTCOME_FAILURE,
        failureCategory = Some("STREAMING_ERROR"),
        errorDetail = Some(ex.getMessage),
        processingPath = "UpscanConnector.getUpscanFile.recover"
      )
      throw new RuntimeException("Error Streaming file from Upscan service")
    }
  }

  /** Emits an enriched audit of a single downstream Upscan file-retrieval call so each bulk file read can be traced
    * end-to-end across RAS-API and Upscan in Splunk. Records the downstream service, the file reference, the user
    * identifier, the correlation id, the outcome, and (on failure) the failure category and error detail so that
    * retrieval failures are identifiable. The pre-signed download URL is deliberately never audited as it may carry
    * credentials.
    */
  private def auditUpscanCall(
    userId: String,
    reference: String,
    corrId: String,
    outcome: String,
    failureCategory: Option[String],
    errorDetail: Option[String],
    processingPath: String
  )(using hc: HeaderCarrier): Unit = {

    val auditData: Map[String, String] = Map(
      "downstreamService" -> UPSCAN_DOWNSTREAM_SERVICE,
      "fileReference"     -> reference,
      "userIdentifier"    -> userId,
      "correlationId"     -> corrId,
      "outcome"           -> outcome,
      "auditEventType"    -> UPSCAN_CALL_AUDIT_TYPE,
      "processingPath"    -> processingPath
    ) ++
      failureCategory.map("failureCategory" -> _) ++
      errorDetail.map("errorDetail" -> _)

    auditService.audit(
      auditType = UPSCAN_CALL_AUDIT_TYPE,
      path = reference,
      auditData = auditData
    )
  }

}
