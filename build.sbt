
import play.core.PlayVersion
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings

val appName = "ras-api"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala,SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory): _*)
  .disablePlugins(JUnitXmlReportPlugin)
  .configs(IntegrationTest)

resolvers ++= Seq(
  Resolver.bintrayRepo("hmrc", "releases"),
  Resolver.jcenterRepo
)

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
ScoverageKeys.coverageMinimum           := 70
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
val akkaVersion = "2.5.23"
val excludeIteratees = ExclusionRule("com.typesafe.play", "play-iteratees_2.12")
lazy val silencerVersion = "1.7.1"

//compile dependencies
libraryDependencies ++= Seq(
  ws,
  "uk.gov.hmrc"       %% "bootstrap-play-26"    % "2.3.0",
  "uk.gov.hmrc"       %% "domain"               % "5.6.0-play-26",
  "uk.gov.hmrc"       %% "mongo-caching"        % "6.15.0-play-26" excludeAll excludeIteratees,
  "uk.gov.hmrc"       %% "simple-reactivemongo" % "7.31.0-play-26" excludeAll excludeIteratees,
  "uk.gov.hmrc"       %% "json-encryption"      % "4.5.0-play-26",
  "uk.gov.hmrc"       %% "play-hmrc-api"        % "4.1.0-play-26",
  "uk.gov.hmrc"       %% "http-caching-client"  % "9.0.0-play-26",
  "com.typesafe.play" %% "play-iteratees-reactive-streams" % "2.6.1",
  "joda-time"         % "joda-time"             % "2.10.9",
  compilerPlugin("com.github.ghik" % "silencer-plugin" % silencerVersion cross CrossVersion.full),
                              "com.github.ghik" % "silencer-lib"    % silencerVersion % Provided cross CrossVersion.full
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
  "uk.gov.hmrc"               %% "hmrctest"           % "3.9.0-play-26"   % scope,
  "org.scalatest"             %% "scalatest"          % "3.0.9"           % scope,
  "org.pegdown"               %  "pegdown"            % "1.6.0"           % scope,
  "org.scalatestplus.play"    %% "scalatestplus-play" % "3.1.3"           % scope,
  "org.mockito"               %  "mockito-core"       % "3.3.3"           % scope,
  "uk.gov.hmrc"               %% "reactivemongo-test" % "4.21.0-play-26"  % scope excludeAll excludeIteratees,
  "com.typesafe.akka"         %  "akka-testkit_2.12"  % akkaVersion       % scope,
  "de.leanovate.play-mockws"  %% "play-mockws"        % "2.6.6"           % scope excludeAll excludeIteratees,
  "com.github.tomakehurst"    %  "wiremock-jre8"      % "2.21.0"          % scope
)

scalacOptions ++= Seq(
  "-P:silencer:pathFilters=views;routes"
)
