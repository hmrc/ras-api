import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object MicroServiceBuild extends Build with MicroService {

  val appName = "ras-api"

  override lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  private val apiPlatformlibVersion = "2.1.0"
  private val playReactivemongoVersion = "6.1.0"
  private val jsonEncryptionVersion = "3.2.0"
  private val akkaVersion = "2.4.10"


  val compile = Seq(
   ws,
    "uk.gov.hmrc" %% "microservice-bootstrap" % "6.15.0",
    "uk.gov.hmrc" %% "auth-client" % "2.5.0",
    "uk.gov.hmrc" %% "domain" % "5.1.0",
    "uk.gov.hmrc" %% "mongo-caching" % "5.3.0",
    "uk.gov.hmrc" %% "play-reactivemongo" % playReactivemongoVersion,
    "uk.gov.hmrc" %% "json-encryption" % jsonEncryptionVersion,
    "uk.gov.hmrc" %% "play-hmrc-api" % apiPlatformlibVersion,
    "com.typesafe.akka" % "akka-actor_2.11" % akkaVersion,
    "com.typesafe.akka" % "akka-testkit_2.11" % akkaVersion,
    "uk.gov.hmrc" %% "http-caching-client" % "7.1.0",
    "joda-time" % "joda-time" % "2.7.0")

  def test(scope: String = "test,it") = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "2.3.0" % scope,
    "org.scalatest" %% "scalatest" % "2.2.6" % scope,
    "org.pegdown" % "pegdown" % "1.6.0" % scope,
    "com.typesafe.play" %% "play-test" % PlayVersion.current % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.1"  % scope,
    "org.mockito" % "mockito-core" % "1.9.0" % scope,
    "com.typesafe.play" %% "play-specs2" % PlayVersion.current % scope,
    "uk.gov.hmrc" %% "reactivemongo-test" % "3.1.0" % scope,
    "com.typesafe.akka" % "akka-testkit_2.11" % akkaVersion % scope,
    "de.leanovate.play-mockws" %% "play-mockws" % "2.6.2" % scope
  )

}
