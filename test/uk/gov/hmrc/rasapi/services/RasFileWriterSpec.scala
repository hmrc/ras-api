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

import java.nio.file.Files

import org.scalatest.BeforeAndAfter
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatestplus.play.OneServerPerSuite
import uk.gov.hmrc.play.test.UnitSpec

import scala.collection.mutable.ListBuffer
import scala.io.Source

class RasFileWriterSpec extends UnitSpec with OneServerPerSuite with ScalaFutures with MockitoSugar with BeforeAndAfter {
  object fileWriter extends RasFileWriter
  val resultsArr = Array("456C,John,Smith,1990-02-21,nino-INVALID_FORMAT",
    "AB123456C,John,Smith,1990-02-21,MATCHING_FAILED",
    "AB123456C,John,Smith,1990-02-21,otherUKResident,scotResident")

  val resultsList = ListBuffer("456C,John,Smith,1990-02-21,nino-INVALID_FORMAT",
    "AB123456C,John,Smith,1990-02-21,MATCHING_FAILED",
    "AB123456C,John,Smith,1990-02-21,otherUKResident,scotResident")

  val userId: String = "A1234567"

  "RasFileWriter" should {
    "create a FileWriter for a tempFile" in {
      val res = fileWriter.createFileWriter("1234", userId)
      Files.exists(res._1) shouldBe true
      Files.deleteIfExists(res._1)

    }

    "writes data to the file " in {
      val res = fileWriter.createFileWriter("5678", userId)
      Files.exists(res._1) shouldBe true
      resultsArr.foreach(str => fileWriter.writeResultToFile(res._2,str, userId))
      fileWriter.closeWriter(res._2)
      val lines = Source.fromFile(res._1.toFile).getLines().toArray
//      lines.size shouldBe 3
      lines should contain theSameElementsAs resultsArr
      Files.deleteIfExists(res._1)
    }

    "closes fileWriter " in {
      val res = fileWriter.createFileWriter("789", userId)
      Files.exists(res._1) shouldBe true
      resultsArr.foreach(str => fileWriter.writeResultToFile(res._2,str, userId))
      fileWriter.closeWriter(res._2) shouldBe true

    }
  }
}
