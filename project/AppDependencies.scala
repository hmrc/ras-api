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

  val hmrcMongoVersion = "1.3.0"
  val bootstrapVersion = "7.20.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-28"        % bootstrapVersion,
    "uk.gov.hmrc"                  %% "domain"                           % "8.3.0-play-28",
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-28"               % hmrcMongoVersion,
    "uk.gov.hmrc"                  %% "play-hmrc-api"                    % "7.2.0-play-28",
    "uk.gov.hmrc"                  %% "http-caching-client"              % "10.0.0-play-28",
    "joda-time"                    %  "joda-time"                        % "2.12.5",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"             % "2.15.2"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"               %% "bootstrap-test-play-28"   % bootstrapVersion,
    "org.scalatest"             %% "scalatest"                % "3.2.16",
    "org.scalatestplus"         %% "mockito-4-11"             % "3.2.16.0",
    "uk.gov.hmrc.mongo"         %% "hmrc-mongo-test-play-28"  % hmrcMongoVersion,
    "com.typesafe.akka"         %% "akka-testkit"             % "2.6.21",
    "de.leanovate.play-mockws"  %% "play-mockws"              % "2.8.1",
    "com.github.tomakehurst"    %  "wiremock-jre8"            % "2.35.0",
    "com.vladsch.flexmark"      %  "flexmark-all"             % "0.64.8"
  ).map(_ % "it, test")

  def apply(): Seq[sbt.ModuleID] = compile ++ test
}
