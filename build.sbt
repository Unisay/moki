name := "moki"

version := "5.0.1"

organization := "com.github.unisay"

scalaVersion := "2.12.1"

scalaOrganization in ThisBuild := "org.typelevel"

licenses += ("MIT", url("http://opensource.org/licenses/MIT"))

scalacOptions ++= Seq(
  "-unchecked",
  "-feature",
  "-deprecation:false",
  "-Xlint",
  "-Xcheckinit",
  "-Ypartial-unification",
  "-Yinduction-heuristics",
  "-Ywarn-unused-import",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-Ywarn-dead-code",
  "-Yno-adapted-args",
  "-language:_",
  "-target:jvm-1.8",
  "-encoding", "UTF-8"
)

resolvers += Resolver.sonatypeRepo("releases")

lazy val moki = (project in file("."))
  .settings(
    libraryDependencies ++= Deps.all,
    fork in Test := true
  )
