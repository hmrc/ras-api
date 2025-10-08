import sbt.Keys.scalacOptions
import uk.gov.hmrc.DefaultBuildSettings.itSettings

import scala.collection.Seq

ThisBuild / scalaVersion        := "2.13.16"
ThisBuild / majorVersion        := 1

lazy val microservice = Project("ras-api", file("."))
  .enablePlugins(play.sbt.PlayScala, SbtDistributablesPlugin)
  .disablePlugins(JUnitXmlReportPlugin)
  .settings(Compile / unmanagedResourceDirectories += baseDirectory.value / "resource")
  .settings(CodeCoverageSettings())
  .settings(
    PlayKeys.playDefaultPort := 9669,
    libraryDependencies ++= AppDependencies(),
    scalacOptions ++= Seq("-Wconf:src=routes/.*:s")
  )

lazy val it = project
  .enablePlugins(PlayScala)
  .dependsOn(microservice % "test->test")
  .settings(itSettings())
