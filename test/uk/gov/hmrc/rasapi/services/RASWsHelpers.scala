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

import java.nio.file.Paths

import mockws.MockWS
import play.api.libs.Files
import play.api.mvc._
import play.api.test.Helpers._
import scala.io.Source

trait RASWsHelpers extends Controller {
  val ws = MockWS {
    case (POST, "/") => upload
  }

  def upload: Action[MultipartFormData[Files.TemporaryFile]] =
    Action(parse.multipartFormData) { request =>
      request.body
        .file("rasFileKey")
        .map { file =>
          // only get the last part of the filename
          // otherwise someone can send a path like ../../home/foo/bar.txt to write to other files on the system
          val filename = Paths.get(file.filename).getFileName

          val lines = Source.fromFile(filename.toFile).getLines.toArray

          Ok("File uploaded")
        }
        .getOrElse {
          Ok("ERROR")
        }
    }
}
