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

package uk.gov.hmrc.rasapi.repositories

import play.api.Logging
import play.api.libs.iteratee.Iteratee
import uk.gov.hmrc.rasapi.services.RasFileWriter

import java.io.{BufferedWriter, FileWriter}
import java.nio.file.{Files, Path}
import scala.util.Random

object TestFileWriter extends RasFileWriter with Logging {

  def generateFile(data: Iterator[Any]): Path = {
    val file = Files.createTempFile(Random.nextInt().toString,".csv")
    val outputStream = new BufferedWriter(new FileWriter(file.toFile))
    try {
      data.foreach { line => outputStream.write(line.toString)
        outputStream.newLine()
      }

      file
    }
    catch {
      case ex: Throwable => logger.error("Error creating file" + ex.getMessage)
        outputStream.close() ;throw new RuntimeException("Exception in generating file" + ex.getMessage)
    }
    finally outputStream.close()
  }
}

object RepositoriesHelper {
  lazy val createFile: Path = TestFileWriter.generateFile(resultsArr.iterator)
  val resultsArr: Array[String] = Array("456C,John,Smith,1990-02-21,nino-INVALID_FORMAT",
    "AB123456C,John,Smith,1990-02-21,NOT_MATCHED",
    "AB123456C,John,Smith,1990-02-21,otherUKResident,scotResident")
  def getAll: Iteratee[Array[Byte], Array[Byte]] = Iteratee.consume[Array[Byte]]()
}




