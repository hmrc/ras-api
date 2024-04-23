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

import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {

  val hmrcMongoVersion = "1.9.0"
  val bootstrapVersion = "8.5.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30"        % bootstrapVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"               % hmrcMongoVersion,
    "uk.gov.hmrc"                  %% "domain"                           % "8.3.0-play-28",
    "uk.gov.hmrc"                  %%  "play-hmrc-api-play-30"           % "8.0.0",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"             % "2.17.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"               %% "bootstrap-test-play-30"   % bootstrapVersion,
    "uk.gov.hmrc.mongo"         %% "hmrc-mongo-test-play-30"  % hmrcMongoVersion,
    "org.scalatest"             %% "scalatest"                % "3.2.18",
    "org.scalatestplus"         %% "mockito-5-10"             % "3.2.18.0",
    "de.leanovate.play-mockws"  %% "play-mockws-3-0"          % "3.0.3",
    "org.wiremock"              % "wiremock-standalone"       % "3.5.3",
    "com.vladsch.flexmark"      %  "flexmark-all"             % "0.64.8"
  ).map(_ % "it, test")

  def apply(): Seq[sbt.ModuleID] = compile ++ test
}
