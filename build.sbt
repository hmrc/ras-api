
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings

val appName = "ras-api"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtDistributablesPlugin): _*)
  .disablePlugins(JUnitXmlReportPlugin)
  .configs(IntegrationTest)

// scoverage settings
ScoverageKeys.coverageExcludedPackages  := "<empty>;" +
  "testOnlyDoNotUseInAppConf.*;" +
  "uk.gov.hmrc.rasapi.config.*;" +
  "conf.*;" +
  "prod;" +
  "app;" +
  "uk.gov.hmrc;" +
  "uk.gov.hmrc.rasapi.views.*;" +
  "definition.*;" +
  "ras.*;" +
  "uk.gov.hmrc.rasapi.controllers.Documentation;" +
  "dev.*;" +
  "matching.*"
ScoverageKeys.coverageMinimum           := 80
ScoverageKeys.coverageFailOnMinimum     := false
ScoverageKeys.coverageHighlighting      := true
parallelExecution in Test               := false

// build settings
majorVersion := 1

SbtDistributablesPlugin.publishingSettings
DefaultBuildSettings.defaultSettings()
PlayKeys.playDefaultPort := 9669

scalaVersion                      := "2.12.12"
retrieveManaged                   := true
evictionWarningOptions in update  := EvictionWarningOptions.default.withWarnScalaVersionEviction(false)
routesGenerator                   := InjectedRoutesGenerator

// IT Settings
DefaultBuildSettings.integrationTestSettings()

unmanagedResourceDirectories in Compile += baseDirectory.value / "resources"

// dependencies
val akkaVersion = "2.6.14"
val excludeIteratees = ExclusionRule("com.typesafe.play", "play-iteratees_2.12")
lazy val silencerVersion = "1.7.1"

//compile dependencies
libraryDependencies ++= Seq(
  ws,
  "uk.gov.hmrc"       %% "bootstrap-backend-play-27"    % "5.2.0",
  "uk.gov.hmrc"       %% "domain"                       % "5.11.0-play-27",
  "uk.gov.hmrc.mongo" %% "hmrc-mongo-play-27"           % "0.50.0",
  "uk.gov.hmrc"       %% "json-encryption"              % "4.10.0-play-27",
  "uk.gov.hmrc"       %% "play-hmrc-api"                % "6.2.0-play-27",
  "uk.gov.hmrc"       %% "http-caching-client"          % "9.4.0-play-27",
  "com.typesafe.play" %% "play-iteratees-reactive-streams" % "2.6.1",
  "joda-time"         % "joda-time"                     % "2.10.9",
  compilerPlugin("com.github.ghik"          % "silencer-plugin" % silencerVersion cross CrossVersion.full),
                              "com.github.ghik"         % "silencer-lib"    % silencerVersion % Provided cross CrossVersion.full
)

dependencyOverrides ++= Seq(
  "com.typesafe.akka" %% "akka-actor_2.12" % akkaVersion force(),
  "com.typesafe.akka" %% "akka-stream"     % akkaVersion force(),
  "com.typesafe.akka" %% "akka-protobuf"   % akkaVersion force(),
  "com.typesafe.akka" %% "akka-slf4j"      % akkaVersion force(),
  "com.typesafe.akka" %% "akka-actor"      % akkaVersion force()
)

// test dependencies
val scope = "test,it"

libraryDependencies ++= Seq(
  "org.scalatest"             %% "scalatest"                  % "3.0.9"          % scope,
  "org.pegdown"               %  "pegdown"                    % "1.6.0"          % scope,
  "uk.gov.hmrc.mongo"         %%  "hmrc-mongo-test-play-27"   % "0.50.0"         % scope,
  "org.scalatestplus.play"    %% "scalatestplus-play"         % "4.0.0"          % scope,
  "org.mockito"               %  "mockito-core"               % "3.9.0"          % scope,
  "com.typesafe.akka"         %  "akka-testkit_2.12"          % akkaVersion      % scope,
  "de.leanovate.play-mockws"  %% "play-mockws"                % "2.6.6"          % scope excludeAll excludeIteratees,
  "com.github.tomakehurst"    %  "wiremock-jre8"              % "2.21.0"         % scope
)

scalacOptions ++= Seq(
  "-P:silencer:pathFilters=views;routes"
)
