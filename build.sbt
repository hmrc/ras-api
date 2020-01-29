import play.sbt.PlayImport.PlayKeys
import play.sbt.routes.RoutesKeys.{routesGenerator, _}
import sbt.Keys._
import sbt._
import uk.gov.hmrc.DefaultBuildSettings.{defaultSettings, scalaSettings}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin.publishingSettings
import uk.gov.hmrc.versioning.SbtGitVersioning
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion
import uk.gov.hmrc.{SbtArtifactory, SbtAutoBuildPlugin}
import com.lucidchart.sbt.scalafmt.ScalafmtCorePlugin.autoImport.scalafmtOnCompile

val appName = "ras-api"

lazy val plugins: Seq[Plugins] =
  Seq(play.sbt.PlayScala,
      SbtAutoBuildPlugin,
      SbtGitVersioning,
      SbtDistributablesPlugin,
      SbtArtifactory)

lazy val excludedPackages: Seq[String] = Seq(
  "<empty>",
  "testOnlyDoNotUseInAppConf.*",
  "uk.gov.hmrc.rasapi.config.*",
  "conf.*",
  "prod",
  "app",
  "uk.gov.hmrc",
  "uk.gov.hmrc.rasapi.views.*",
  "definition.*",
  "ras.*",
  "uk.gov.hmrc.rasapi.controllers.Documentation",
  "dev.*",
  "matching.*"
)

lazy val scoverageSettings = {
  import scoverage.ScoverageKeys
  Seq(
    ScoverageKeys.coverageExcludedPackages := "<empty>;testOnlyDoNotUseInAppConf.*;uk.gov.hmrc.rasapi.config.*;conf.*;prod;app;uk.gov.hmrc;uk.gov.hmrc.rasapi.views.*;definition.*;ras.*;uk.gov.hmrc.rasapi.controllers.Documentation;dev.*;matching.*  ",
    ScoverageKeys.coverageMinimum := 70,
    ScoverageKeys.coverageFailOnMinimum := false,
    ScoverageKeys.coverageHighlighting := true,
    parallelExecution in Test := false
  )
}

lazy val microservice = Project(appName, file("."))
  .enablePlugins(plugins: _*)
  .settings(
    PlayKeys.playDefaultPort := 9669,
    defaultSettings(),
    scoverageSettings,
    publishingSettings,
    scalaSettings,
    majorVersion := 1,
    libraryDependencies ++= AppDependencies.all,
    retrieveManaged := true,
    evictionWarningOptions in update := EvictionWarningOptions.default
      .withWarnScalaVersionEviction(false),
    routesGenerator := StaticRoutesGenerator,
    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources",
    scalafmtOnCompile := true,
    resolvers ++= Seq(Resolver.bintrayRepo("hmrc", "releases"),
                      Resolver.jcenterRepo)
  )
