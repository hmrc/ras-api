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

import akka.actor.ActorSystem
import play.api.Logging
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.rasapi.connectors.FileUploadConnector

import java.io._
import java.nio.file.{Files, Path}
import scala.concurrent.{ExecutionContext, Future}
import scala.io.{Codec, Source}
import scala.util.Try

trait RasFileReader extends Logging {
  implicit val system: ActorSystem = ActorSystem()
  implicit val ec: ExecutionContext

  val fileUploadConnector: FileUploadConnector

  def readFile(envelopeId: String, fileId: String, userId: String)(implicit hc: HeaderCarrier): Future[Iterator[String]] = {

    implicit val codec: Codec = Codec.ISO8859

    fileUploadConnector.getFile(envelopeId, fileId, userId).map{

      case Some(inputStream) => Source.fromInputStream(inputStream).getLines
      case None => logger.error(s"[RasFileReader][readFile] File Processing: the file ($fileId) could not be found for userId ($userId).")
        throw new FileNotFoundException
    }
  }
}

trait RasFileWriter extends Logging {

  type FILE_INFO = (Path,BufferedWriter)

  def createFileWriter(fileId:String, userId: String) : FILE_INFO = {
    val file = Try(Files.createTempFile(fileId,".csv"))
    if (file.isSuccess) {
      (file.get, new BufferedWriter(new FileWriter(file.get.toFile)))
    } else {
      logger.error(s"[RasFileReader][createFileWriter] Error creating temp file for writing results for userId ($userId).")
      throw new FileNotFoundException
    }
  }

  def writeResultToFile(writer:BufferedWriter, line: String, userId: String): Boolean = {
    try {
      writer.write(line)
      writer.newLine()
    }
    catch {
      case ex: Throwable => logger.error(s"[RasFileReader][writeResultToFile] Exception in writing line to the results file ${ex.getMessage} for userId ($userId).", ex)
        throw new RuntimeException(s"Exception in writing line to the results file ${ex.getMessage}")
    }
    true
  }

  def closeWriter(writer:BufferedWriter):Boolean ={
    val res = Try(writer.close())
     res.recover{
       case ex: Throwable => logger.error(s"[RasFileReader][writeResultToFile] Failed to close the Outputstream with error ${ex.getMessage}.", ex)
         false
     }
    true
  }

  def clearFile(path:Path, userId: String) :Unit =
    if (!Files.deleteIfExists(path))
      logger.error(s"[RasFileReader][clearFile] error deleting file or file $path doesn't exist for userId ($userId).")
}
