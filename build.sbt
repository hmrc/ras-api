
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
val apiPlatformlibVersion = "4.1.0-play-25"
val jsonEncryptionVersion = "4.5.0-play-25"
val akkaVersion = "2.5.18"
val excludeIteratees = ExclusionRule("com.typesafe.play", "play-iteratees_2.11")

//compile dependencies
libraryDependencies ++= Seq(
  ws,
  "uk.gov.hmrc"       %% "bootstrap-play-25"    % "5.1.0",
  "uk.gov.hmrc"       %% "domain"               % "5.6.0-play-25",
  "uk.gov.hmrc"       %% "mongo-caching"        % "6.10.0-play-25" excludeAll excludeIteratees,
  "uk.gov.hmrc"       %% "simple-reactivemongo" % "7.25.0-play-25" excludeAll excludeIteratees,
  "uk.gov.hmrc"       %% "json-encryption"      % jsonEncryptionVersion,
  "uk.gov.hmrc"       %% "play-hmrc-api"        % apiPlatformlibVersion,
  "uk.gov.hmrc"       %% "http-caching-client"  % "9.0.0-play-25",
  "joda-time"         % "joda-time"             % "2.7.0"
)

dependencyOverrides ++= Set(
  "com.typesafe.akka" % "akka-actor_2.11"       % akkaVersion
)

// test dependencies
val scope = "test,it"

libraryDependencies ++= Seq(
  "uk.gov.hmrc"               %% "hmrctest"           % "3.9.0-play-25"   % scope,
  "org.scalatest"             %% "scalatest"          % "3.0.8"           % scope,
  "org.pegdown"               % "pegdown"             % "1.6.0"           % scope,
  "org.scalatestplus.play"    %% "scalatestplus-play" % "2.0.1"           % scope,
  "org.mockito"               % "mockito-core"        % "1.9.5"           % scope,
  "uk.gov.hmrc"               %% "reactivemongo-test" % "4.16.0-play-25"  % scope excludeAll excludeIteratees,
  "com.typesafe.akka"         % "akka-testkit_2.11"   % akkaVersion       % scope,
  "de.leanovate.play-mockws"  %% "play-mockws"        % "2.6.6"           % scope excludeAll excludeIteratees,
  "com.github.tomakehurst"    % "wiremock"            % "2.26.3"          % scope
)