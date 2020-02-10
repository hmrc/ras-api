/*
 * Copyright 2019 HM Revenue & Customs
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

package uk.gov.hmrc.rasapi

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat
import play.api.data.validation.ValidationError
import play.api.libs.json._
import reactivemongo.api.BSONSerializationPack
import reactivemongo.api.gridfs.ReadFile
import reactivemongo.bson.BSONValue

package object models {

  type NINO = String
  type Name = String
  type ResultsFile = ReadFile[BSONSerializationPack.type, BSONValue]

  object JsonReads {

    import Extensions._

    private val invalidDataType = "INVALID_DATA_TYPE"
    private val invalidFormat = "INVALID_FORMAT"
    private val missing = "MISSING_FIELD"
    private val invalidDateValidation = "INVALID_DATE"
    private val ninoRegex = "^((?!(BG|GB|KN|NK|NT|TN|ZZ)|(D|F|I|Q|U|V)[A-Z]|[A-Z](D|F|I|O|Q|U|V))[A-Z]{2})[0-9]{6}[A-D]?$"

    private val isoDatePattern = Map(
      "yyyy-MM-dd" -> "^[\\d]{4}-[\\d]{2}-[\\d]{2}$"
    )

    private val bulkDatePatterns = isoDatePattern ++ Map(
      "dd/MM/yyyy" -> "^[\\d]{2}/[\\d]{2}/[\\d]{4}$",
      "dd-MM-yyyy" -> "^[\\d]{2}-[\\d]{2}-[\\d]{4}$",
      "yyyy/MM/dd" -> "^[\\d]{4}/[\\d]{2}/[\\d]{2}$"
    )

    val nino: Reads[NINO] = ninoReads()
    val name: Reads[Name] = nameReads()
    val isoDate: Reads[DateTime] = dateReads(isoDatePattern)
    val bulkDate: Reads[DateTime] = dateReads(bulkDatePatterns)

    private def ninoReads(): Reads[NINO] = new Reads[NINO] {

      def reads(json: JsValue): JsResult[NINO] = {
        json match {
          case JsString(data) => data match {
            case strValue if strValue.isEmpty => JsError(Seq(JsPath() -> Seq(ValidationError(missing))))
            case strValue if !strValue.toUpperCase.matches(ninoRegex) => JsError(Seq(JsPath() -> Seq(ValidationError(invalidFormat))))
            case strValue => JsSuccess(strValue)
          }
          case _ => JsError(Seq(JsPath() -> Seq(ValidationError(invalidDataType))))
        }
      }
    }

    /**
      * Ensures that a name only contains specific characters and is between 1 and 35 characters long,
      * and does not contain solo blank spaces e.g. " ".
      *
      * @return
      */
    private def nameReads(): Reads[Name] = new Reads[Name] {

      def reads(json: JsValue): JsResult[Name] = {

        json match {
          case JsString(data) => data match {
            case strValue if strValue.trim.isEmpty => JsError(Seq(JsPath() -> Seq(ValidationError(missing))))
            case strValue if !strValue.matches("^[a-zA-Z &`\\-\\'^]{1,35}$") => JsError(Seq(JsPath() -> Seq(ValidationError(invalidFormat))))
            case strValue => JsSuccess(strValue)
          }
          case _ => JsError(Seq(JsPath() -> Seq(ValidationError(invalidDataType))))
        }
      }
    }

    /**
      * Reads a JSON value as a joda.org.time.DateTime object.
      * This date cannot be in the future.
      *
      * @param patterns
      */
    private def dateReads(patterns: Map[String, String]): Reads[DateTime] = new Reads[DateTime] {
      def reads(json: JsValue): JsResult[DateTime] = json match {
        case JsString(s) if !s.trim.isEmpty =>
          s.extractDateFormat(patterns) match {
            case Some(format) =>
              s.toDateTime(format) match {
                case Some(d: DateTime) if !d.isAfterNow => JsSuccess(d)
                case _ => JsError(Seq(JsPath() -> Seq(ValidationError(invalidDateValidation))))
              }
            case None => JsError(Seq(JsPath() -> Seq(ValidationError(invalidFormat))))
          }
        case JsString(s) if s.trim.isEmpty => JsError(Seq(JsPath() -> Seq(ValidationError(missing))))
        case _ => JsError(Seq(JsPath() -> Seq(ValidationError(invalidDataType))))
      }
    }
  }

  object Extensions {

    implicit class StringDateUtils(date: String) {

      /**
        * Given a map of date formats (ie dd/mm/yyyy) and date regexes (ie [\\d]{2}/[\\d]{2}/[\\d]{4}),
        * it will find the first regex in the map for which the string is a match
        * and return the associated date format
        *
        * for example, it will return "dd/mm/yyyy" for an input string like "12/01/1999"
        *
        * @param patterns
        * @return
        */
      def extractDateFormat(patterns: Map[String, String]): Option[String] = {
        patterns.find(pattern => date.matches(pattern._2)) match {
          case Some((format, _)) => Some(format)
          case _ => None
        }
      }

      /**
        * Converts to DateTime a string passed in in a specific format (ie, "dd/mm/yyyy")
        *
        * @param format
        * @return
        */
      def toDateTime(format: String): Option[DateTime] = {
        scala.util.control.Exception.allCatch[DateTime] opt (DateTime.parse(date, DateTimeFormat.forPattern(format)))
      }
    }

  }

}
