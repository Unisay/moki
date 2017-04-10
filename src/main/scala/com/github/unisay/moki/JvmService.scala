package com.github.unisay.moki

import scala.sys.process.{Process, ProcessLogger}
import fs2.Strategy

trait JvmService {

  @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
  def jvmService(mainClass: String,
                 jvmArgs: List[String] = Nil,
                 programArgs: List[String] = Nil,
                 customClasspath: Option[String] = None,
                 processLogger: ProcessLogger = defaultProcessLogger)
                (implicit s: Strategy): TestService[Process] =
    processService(composeArguments(mainClass, jvmArgs, customClasspath, programArgs)
      .getOrElse(sys.error("Failed to initialize JVM process")), processLogger)

  private def composeArguments(mainClass: String,
                               jvmArgs: List[String],
                               customClasspath: Option[String],
                               programArgs: List[String]): Option[List[String]] = {
    for {
      fileSeparator <- sys.props.get("file.separator")
      pathSeparator <- sys.props.get("path.separator")
      javaHome <- sys.props.get("java.home")
      javaPath = javaHome + fileSeparator + "bin" + fileSeparator + "java"
      cp <- sys.props.get("java.class.path").flatMap(path => customClasspath.map(path + pathSeparator + _))
    } yield (javaPath :: jvmArgs) ++ ("-cp" :: cp :: mainClass :: programArgs)
  }
}

object JvmService extends JvmService
