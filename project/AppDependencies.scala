import play.sbt.PlayImport.ws
import sbt.*

object AppDependencies {

  private val hmrcMongoVersion = "2.10.0"
  private val bootstrapVersion = "10.4.0"

  private val compile: Seq[ModuleID] = Seq(
    ws,
    "uk.gov.hmrc"                  %% "bootstrap-backend-play-30"        % bootstrapVersion,
    "uk.gov.hmrc.mongo"            %% "hmrc-mongo-play-30"               % hmrcMongoVersion,
    "uk.gov.hmrc"                  %% "domain-play-30"                   % "11.0.0",
    "uk.gov.hmrc"                  %% "play-hmrc-api-play-30"            % "8.0.0"
  )

  private val test: Seq[ModuleID] = Seq(
    "uk.gov.hmrc"               %% "bootstrap-test-play-30"   % bootstrapVersion,
    "uk.gov.hmrc.mongo"         %% "hmrc-mongo-test-play-30"  % hmrcMongoVersion
  ).map(_ % Test)

  def apply(): Seq[sbt.ModuleID] = compile ++ test

}
