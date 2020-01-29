import play.core.PlayVersion
import sbt._

object AppDependencies {

  val compile: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "auth-client" % "2.32.1-play-25",
    "uk.gov.hmrc" %% "domain" % "5.6.0-play-25",
    "uk.gov.hmrc" %% "http-caching-client" % "9.0.0-play-25",
    "uk.gov.hmrc" %% "json-encryption" % "4.4.0-play-25",
    "uk.gov.hmrc" %% "microservice-bootstrap" % "10.9.0",
    "uk.gov.hmrc" %% "mongo-caching" % "6.8.0-play-25",
    "uk.gov.hmrc" %% "play-hmrc-api" % "4.1.0-play-25",
    "uk.gov.hmrc" %% "play-reactivemongo" % "6.8.0",
    "com.typesafe.akka" % "akka-actor_2.11" % "2.5.18",
    "com.typesafe.akka" % "akka-testkit_2.11" % "2.5.18",
    "joda-time" % "joda-time" % "2.7.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc" %% "hmrctest" % "3.9.0-play-25",
    "uk.gov.hmrc" %% "reactivemongo-test" % "4.16.0-play-25",
    "org.scalatest" %% "scalatest" % "3.0.8",
    "org.pegdown" % "pegdown" % "1.6.0",
    "com.typesafe.play" %% "play-test" % PlayVersion.current,
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1",
    "org.mockito" % "mockito-core" % "3.2.4",
    "com.typesafe.play" %% "play-specs2" % PlayVersion.current,
    "com.typesafe.akka" % "akka-testkit_2.11" % "2.5.18",
    "de.leanovate.play-mockws" %% "play-mockws" % "2.7.1"
  ).map(_ % "test")

  val all: Seq[ModuleID] = compile ++ test
}
