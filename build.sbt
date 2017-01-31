name := "moki"

version := "1.0"

scalaVersion := "2.12.1"

lazy val moki = (project in file("."))
  .settings(libraryDependencies ++= Deps.all)
