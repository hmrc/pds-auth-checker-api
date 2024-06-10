import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt.Keys.libraryDependencies
import sbt._

object AppDependencies {

  private val bootstrapVersion = "8.5.0"
  

  val compile = Seq(
    "uk.gov.hmrc"             %% "bootstrap-backend-play-30"  % bootstrapVersion,
    "io.lemonlabs"            %% "scala-uri"                  % "3.6.0"
  )

  val test = Seq(
    "uk.gov.hmrc"             %% "bootstrap-test-play-30"     % bootstrapVersion,
    "org.scalamock"           %% "scalamock"                  % "6.0.0",
    "org.scalatestplus"       %% "scalacheck-1-17"            % "3.2.18.0"
  ).map(_ % Test)

  val it = Seq.empty
}
