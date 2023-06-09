
import scoverage.ScoverageKeys
import uk.gov.hmrc.DefaultBuildSettings

val appName = "ras-api"

lazy val microservice = Project(appName, file("."))
  .enablePlugins(Seq(play.sbt.PlayScala, SbtDistributablesPlugin) *)
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
dependencyOverrides ++= AppDependencies.overrides

// build settings
majorVersion := 1

DefaultBuildSettings.defaultSettings()
PlayKeys.playDefaultPort := 9669

scalaVersion                      := "2.13.11"
// To resolve a bug with version 2.x.x of the scoverage plugin - https://github.com/sbt/sbt/issues/6997
// Try to remove when sbt 1.8.0+ and scoverage is 2.0.7+
ThisBuild / libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % VersionScheme.Always
retrieveManaged                   := true
update / evictionWarningOptions   := EvictionWarningOptions.default.withWarnScalaVersionEviction(false)
routesGenerator                   := InjectedRoutesGenerator

// IT Settings
DefaultBuildSettings.integrationTestSettings()

Compile / unmanagedResourceDirectories += baseDirectory.value / "resources"

scalacOptions ++= Seq("-Wconf:src=routes/.*:s")

addCommandAlias("scalastyleAll", "all scalastyle test:scalastyle it:scalastyle")
