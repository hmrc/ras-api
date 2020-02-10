import sbt.Keys._
import sbt.Tests.{Group, SubProcess}
import sbt._
import play.routes.compiler.StaticRoutesGenerator
import scoverage.{ScoverageKeys, ScoverageSbtPlugin}
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion

trait MicroService {

  import uk.gov.hmrc._
  import DefaultBuildSettings.{defaultSettings, addTestReportOption}
  import uk.gov.hmrc.SbtAutoBuildPlugin
  import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
  import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin._
  import uk.gov.hmrc.versioning.SbtGitVersioning
  import uk.gov.hmrc.versioning.SbtGitVersioning.autoImport.majorVersion
  import play.sbt.routes.RoutesKeys.routesGenerator
  import uk.gov.hmrc.SbtArtifactory


  import TestPhases._

  val appName: String

  lazy val appDependencies : Seq[ModuleID] = ???
  lazy val plugins : Seq[Plugins] = Seq.empty
  lazy val playSettings : Seq[Setting[_]] = Seq.empty

  lazy val scoverageSettings = {
    Seq(
      ScoverageKeys.coverageExcludedPackages := "<empty>;testOnlyDoNotUseInAppConf.*;uk.gov.hmrc.rasapi.config.*;conf.*;prod;app;uk.gov.hmrc;uk.gov.hmrc.rasapi.views.*;definition.*;ras.*;uk.gov.hmrc.rasapi.controllers.Documentation;dev.*;matching.*  ",
      ScoverageKeys.coverageMinimum := 70,
      ScoverageKeys.coverageFailOnMinimum := false,
      ScoverageKeys.coverageHighlighting := true,
      parallelExecution in Test := false
    )
  }

  lazy val microservice = Project(appName, file("."))
    .enablePlugins(Seq(play.sbt.PlayScala,SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin, SbtArtifactory) ++ plugins : _*)
    .settings(majorVersion := 1)
    .settings(playSettings : _*)
    .settings(scoverageSettings: _*)
    .settings(publishingSettings: _*)
    .settings(defaultSettings(): _*)
    .settings(majorVersion := 1)
    .settings(
      scalaVersion := "2.11.11",
      libraryDependencies ++= appDependencies,
      retrieveManaged := true,
      evictionWarningOptions in update := EvictionWarningOptions.default.withWarnScalaVersionEviction(false),
      routesGenerator := StaticRoutesGenerator
    ).settings(
    unmanagedResourceDirectories in Compile += baseDirectory.value / "resources"
  ).configs(IntegrationTest)
    .settings(inConfig(IntegrationTest)(Defaults.itSettings): _*)
    .settings(
      Keys.fork in IntegrationTest := false,
      unmanagedSourceDirectories in IntegrationTest <<= (baseDirectory in IntegrationTest)(base => Seq(base / "it")),
      addTestReportOption(IntegrationTest, "int-test-reports"),
      testGrouping in IntegrationTest := oneForkedJvmPerTest((definedTests in IntegrationTest).value),
      parallelExecution in IntegrationTest := false)
      .settings(resolvers ++= Seq(
        Resolver.bintrayRepo("hmrc", "releases"),
        Resolver.jcenterRepo
      ))
}

private object TestPhases {

  def oneForkedJvmPerTest(tests: Seq[TestDefinition]) =
    tests map {
      test => new Group(test.name, Seq(test), SubProcess(ForkOptions(runJVMOptions = Seq("-Dtest.name=" + test.name))))
    }
}
