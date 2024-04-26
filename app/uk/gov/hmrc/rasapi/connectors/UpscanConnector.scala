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

package uk.gov.hmrc.rasapi.connectors

import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.stream.scaladsl.StreamConverters
import play.api.Logging
import uk.gov.hmrc.rasapi.config.{AppContext, WSHttp}

import java.io.InputStream
import javax.inject.Inject
import scala.concurrent.{ExecutionContext, Future}

class UpscanConnector @Inject()(val wsHttp: WSHttp,
                                val appContext: AppContext,
                                implicit val ec: ExecutionContext
                                   ) extends Logging {

  def getUpscanFile(downloadUrl: String, reference: String, userId: String): Future[Option[InputStream]] = {
    implicit val system: ActorSystem = ActorSystem()
    logger.info(s"[UpscanConnector][getUpscanFile] Trying to retrieve file from Upscan: $reference")

    wsHttp.buildRequestWithStream(uri = downloadUrl).map { res =>
      Some(res.bodyAsSource.runWith(StreamConverters.asInputStream()))
    } recover {
      case ex: Throwable =>
        logger.error(s"[UpscanConnector][getUpscanFile] Exception thrown while retrieving file / converting to InputStream for userId ($userId). ${ex.getMessage}}.", ex)
        throw new RuntimeException("Error Streaming file from Upscan service")
    }
  }
}
