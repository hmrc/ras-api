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

package uk.gov.hmrc.rasapi.services

import java.io._
import java.nio.file.{Files, Path}

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import play.api.Logger
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.rasapi.connectors.FileUploadConnector

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.io.Source
import scala.util.Try

import scala.io.Codec

trait RasFileReader {
  implicit val system = ActorSystem()
  implicit val materializer:ActorMaterializer = ActorMaterializer()

  val fileUploadConnector: FileUploadConnector

  def readFile(envelopeId: String, fileId: String, userId: String)(implicit hc: HeaderCarrier): Future[Iterator[String]] = {

    implicit val codec = Codec.ISO8859
    //implicit val codec = Codec.UTF8
    //codec.onMalformedInput(CodingErrorAction.REPLACE)
    //codec.onUnmappableCharacter(CodingErrorAction.REPLACE)

    fileUploadConnector.getFile(envelopeId, fileId, userId).map{

      case Some(inputStream) => Source.fromInputStream(inputStream).getLines
      case None => Logger.error(s"File Processing: problem reading data in the file for userId ($userId).")
        throw new FileNotFoundException
    }
  }
}

trait RasFileWriter {

  type FILE_INFO = (Path,BufferedWriter)

  def createFileWriter(fileId:String, userId: String) : FILE_INFO = {
    val file = Try(Files.createTempFile(fileId,".csv"))
    file.isSuccess match {
      case true =>  (file.get, new BufferedWriter(new FileWriter(file.get.toFile)))
      case false => Logger.error(s"Error creating temp file for writing results for userId ($userId).")
        throw new FileNotFoundException
    }
  }

  def writeResultToFile(writer:BufferedWriter, line: String, userId: String): Boolean = {
    try {
      writer.write(line)
      writer.newLine()
    }
    catch {
      case ex: Throwable => Logger.error(s"Exception in writing line to the results file ${ex.getMessage} for userId ($userId).")
        throw new RuntimeException(s"Exception in writing line to the results file ${ex.getMessage}")
    }
    true
  }

  def closeWriter(writer:BufferedWriter):Boolean ={
    val res = Try(writer.close())
     res.recover{
       case ex:Throwable => Logger.error(s"Failed to close the Outputstream with error ${ex.getMessage}.")
         false
     }
    true
  }

  def clearFile(path:Path, userId: String) :Unit =  if (!Files.deleteIfExists(path)) Logger.error(s"error deleting file or file ${path} doesn't exist for userId ($userId).")
}
