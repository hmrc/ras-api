import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {

  val hmrcMongoVersion = "2.9.0"
  val bootstrapVersion = "9.19.0"

  val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30"        % bootstrapVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"               % hmrcMongoVersion,
    "uk.gov.hmrc"                  %% "domain"                           % "8.3.0-play-28",
    "uk.gov.hmrc"                  %% "play-hmrc-api-play-30"            % "8.0.0",
    "com.fasterxml.jackson.module" %% "jackson-module-scala"             % "2.20.0"
  )

  val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"               %% "bootstrap-test-play-30"   % bootstrapVersion,
    "uk.gov.hmrc.mongo"         %% "hmrc-mongo-test-play-30"  % hmrcMongoVersion,
    "de.leanovate.play-mockws"  %% "play-mockws-3-0"          % "3.0.6"
  ).map(_ % Test)

  def apply(): Seq[sbt.ModuleID] = compile ++ test
}
