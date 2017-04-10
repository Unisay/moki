import sbt._

object Deps {

  private val cats = Seq("org.typelevel" %% "cats-core" % "0.9.0")

  private val fs2 = Seq("co.fs2" %% "fs2-core" % "0.9.5")

  private val fs2Cats = Seq("co.fs2" %% "fs2-cats" % "0.3.0")

  private val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "1.7.22")

  private val logback = Seq("ch.qos.logback" % "logback-classic" % "1.1.9")

  val http4sVer = "0.17.0-M1"
  private val http4s = Seq("http4s-dsl", "http4s-blaze-server", "http4s-blaze-client")
    .map("org.http4s" %% _ % http4sVer)

  private val scalatest = Seq("org.scalatest" %% "scalatest" % "3.0.1")

  private val compile = cats ++ http4s ++ slf4jApi ++ fs2 ++ fs2Cats
  private val test = scalatest ++ logback

  val all: Seq[ModuleID] = compile ++ test.map(_ % "test")
}
