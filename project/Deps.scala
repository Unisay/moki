import sbt._

object Deps {

  private val scalaz = Seq("org.scalaz" %% "scalaz-core" % "7.2.8")

  private val fs2 = Seq("co.fs2" %% "fs2-core" % "0.9.2")

  private val fs2scalaz = Seq("co.fs2" %% "fs2-scalaz" % "0.2.0")

  private val slf4jApi = Seq("org.slf4j" % "slf4j-api" % "1.7.22")

  private val logback = Seq("ch.qos.logback" % "logback-classic" % "1.1.9")

  private val http4s = Seq("dsl", "blaze-server", "blaze-client")
    .map(d => "org.http4s" %% ("http4s-" + d) % "0.15.3a")

  private val shapeless = Seq("com.chuusai" %% "shapeless" % "2.3.2")

  private val scalatest = Seq("org.scalatest" %% "scalatest" % "3.0.1")

  private val compile = scalaz ++ shapeless ++ http4s ++ slf4jApi ++ fs2 ++ fs2scalaz
  private val test = scalatest ++ logback

  val all: Seq[ModuleID] = compile ++ test.map(_ % "test")
}
