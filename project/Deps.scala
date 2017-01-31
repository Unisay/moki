import sbt._

object Deps {

  private val fs2 = Seq("co.fs2" % "fs2-core_2.12" % "0.9.2")

  private val fs2scalaz = Seq("co.fs2" %% "fs2-scalaz" % "0.2.0")

  private val slf4j = Seq("api", "simple")
    .map(d => "org.slf4j" % ("slf4j-" + d) % "1.7.22")

  private val http4s = Seq("dsl", "blaze-server", "blaze-client")
    .map(d => "org.http4s" %% ("http4s-" + d) % "0.15.3a")

  private val scalatest = Seq("org.scalatest" % "scalatest_2.12" % "3.0.1")

  private val compile = http4s ++ slf4j ++ fs2 ++ fs2scalaz
  private val test = scalatest

  val all: Seq[ModuleID] = compile ++ test.map(_ % "test")
}
