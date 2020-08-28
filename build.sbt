
import play.core.PlayVersion
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings
import sbt.internals.DslEntry.fromSettingsDef

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

scalaVersion                      := "2.11.12"
retrieveManaged                   := true
evictionWarningOptions in update  := EvictionWarningOptions.default.withWarnScalaVersionEviction(false)
routesGenerator                   := InjectedRoutesGenerator

SbtDistributablesPlugin.publishingSettings
DefaultBuildSettings.defaultSettings()
PlayKeys.playDefaultPort := 9669

// IT Settings
DefaultBuildSettings.integrationTestSettings()

unmanagedResourceDirectories in Compile += baseDirectory.value / "resources"

// dependencies
val apiPlatformlibVersion = "4.1.0-play-26"
val jsonEncryptionVersion = "4.5.0-play-26"
val akkaVersion = "2.5.23"
val excludeIteratees = ExclusionRule("com.typesafe.play", "play-iteratees_2.11")

//compile dependencies
libraryDependencies ++= Seq(
  ws,
  "uk.gov.hmrc"       %% "bootstrap-play-26"    % "1.14.0",
  "uk.gov.hmrc"       %% "domain"               % "5.6.0-play-26",
  "uk.gov.hmrc"       %% "mongo-caching"        % "6.15.0-play-26" excludeAll excludeIteratees,
  "uk.gov.hmrc"       %% "simple-reactivemongo" % "7.30.0-play-26" excludeAll excludeIteratees,
  "uk.gov.hmrc"       %% "json-encryption"      % jsonEncryptionVersion,
  "uk.gov.hmrc"       %% "play-hmrc-api"        % apiPlatformlibVersion,
  "uk.gov.hmrc"       %% "http-caching-client"  % "9.0.0-play-26",
  "com.typesafe.play" %% "play-iteratees-reactive-streams" % "2.6.1",
  "joda-time"         % "joda-time"             % "2.7.0"
)

dependencyOverrides ++= Set(
  "com.typesafe.akka" % "akka-actor_2.11"       % akkaVersion
)

// test dependencies
val scope = "test,it"

libraryDependencies ++= Seq(
  "uk.gov.hmrc"               %% "hmrctest"           % "3.9.0-play-26"   % scope,
  "org.scalatest"             %% "scalatest"          % "3.0.8"           % scope,
  "org.pegdown"               % "pegdown"             % "1.6.0"           % scope,
  "org.scalatestplus.play"    %% "scalatestplus-play" % "3.1.3"           % scope,
  "org.mockito"               % "mockito-core"        % "3.3.3"         % scope,
  "uk.gov.hmrc"               %% "reactivemongo-test" % "4.21.0-play-26"  % scope excludeAll excludeIteratees,
  "com.typesafe.akka"         % "akka-testkit_2.11"   % akkaVersion       % scope,
  "de.leanovate.play-mockws"  %% "play-mockws"        % "2.6.6"           % scope excludeAll excludeIteratees,
  "com.github.tomakehurst"    % "wiremock-jre8"       % "2.21.0"          % scope
)