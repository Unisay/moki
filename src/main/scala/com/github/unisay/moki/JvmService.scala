package com.github.unisay.moki

import java.io._

import com.github.unisay.moki.TestService.TestService

trait JvmService { this: ProcessService =>

  def jvmService(mainClass: String,
                 jvmArgs: List[String] = Nil,
                 programArgs: List[String] = Nil,
                 customClasspath: Option[String] = None,
                 output: PrintStream = System.out,
                 forceStop: Boolean = false): TestService[Process] =
    TestService(
      startTask = composeArguments(mainClass, jvmArgs, customClasspath, programArgs)
          .fold(throw new RuntimeException("Failed to initialize JVM process"))(startProcess(output)),
      stopTask = stopProcess(forceStop))

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
