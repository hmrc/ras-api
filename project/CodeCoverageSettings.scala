import sbt.Setting
import scoverage.ScoverageKeys.*

object CodeCoverageSettings {

  private val excludedPackages: Seq[String] = Seq("<empty>", ".*Routes.*","uk.gov.hmrc.rasapi.views.*")

  private val settings: Seq[Setting[_]] = Seq(
    coverageExcludedPackages := excludedPackages.mkString(";"),
    coverageMinimumStmtTotal := 82,
    coverageFailOnMinimum := true,
    coverageHighlighting := true
  )

  def apply(): Seq[Setting[_]] = settings

}