import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings

val appName = "ras-api"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
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
ScoverageKeys.coverageMinimumStmtTotal  := 80
ScoverageKeys.coverageFailOnMinimum     := true
ScoverageKeys.coverageHighlighting      := true
Test / parallelExecution                := false
libraryDependencies ++= AppDependencies()

// build settings
majorVersion := 1

DefaultBuildSettings.defaultSettings()
PlayKeys.playDefaultPort := 9669

scalaVersion                      := "2.13.16"
retrieveManaged                   := true
routesGenerator                   := InjectedRoutesGenerator

// IT Settings
DefaultBuildSettings.integrationTestSettings()

Compile / unmanagedResourceDirectories += baseDirectory.value / "resources"

scalacOptions ++= Seq("-Wconf:src=routes/.*:s")

addCommandAlias("scalastyleAll", "all scalastyle Test/scalastyle IntegrationTest/scalastyle")
