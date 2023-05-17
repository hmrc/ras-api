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

  val akkaVersion = "2.6.16"
  val excludeIteratees = ExclusionRule("com.typesafe.play", "play-iteratees_2.12")
  val hmrcMongoVersion = "0.68.0"
  val bootstrapVersion = "7.15.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"       %% "bootstrap-backend-play-28"        % bootstrapVersion,
    "uk.gov.hmrc"       %% "domain"                           % "8.2.0-play-28",
    "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-28"               % hmrcMongoVersion,
    "uk.gov.hmrc"       %% "play-hmrc-api"                    % "7.2.0-play-28",
    "uk.gov.hmrc"       %% "http-caching-client"              % "10.0.0-play-28",
    "com.typesafe.play" %% "play-iteratees-reactive-streams"  % "2.6.1",
    "joda-time"         %  "joda-time"                        % "2.12.5",
  )

  val test: Seq[ModuleID] = Seq(
    "org.scalatest"             %% "scalatest"                % "3.2.15",
    "org.scalatestplus"         %% "mockito-3-4"              % "3.2.10.0",
    "org.scalatestplus.play"    %% "scalatestplus-play"       % "5.1.0",
    "org.pegdown"               %  "pegdown"                  % "1.6.0",
    "uk.gov.hmrc.mongo"         %% "hmrc-mongo-test-play-28"  % hmrcMongoVersion,
    "com.typesafe.akka"         %  "akka-testkit_2.12"        % akkaVersion,
    "de.leanovate.play-mockws"  %% "play-mockws"              % "2.8.1" excludeAll excludeIteratees,
    "com.github.tomakehurst"    %  "wiremock-jre8"            % "2.35.0",
    "com.vladsch.flexmark"      %  "flexmark-all"             % "0.64.0"
  ).map(_ % "it, test")

  val overrides: Seq[ModuleID] = Seq(
    "com.typesafe.akka" %% "akka-actor_2.12"            % akkaVersion force(),
    "com.typesafe.akka" %% "akka-stream"                % akkaVersion force(),
    "com.typesafe.akka" %% "akka-protobuf"              % akkaVersion force(),
    "com.typesafe.akka" %% "akka-slf4j"                 % akkaVersion force(),
    "com.typesafe.akka" %% "akka-actor-typed"           % akkaVersion force(),
    "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion force(),
    "com.typesafe.akka" %% "akka-actor"                 % akkaVersion force(),
    "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.14.2" force(),
  )

  def apply(): Seq[sbt.ModuleID] = compile ++ test
}
