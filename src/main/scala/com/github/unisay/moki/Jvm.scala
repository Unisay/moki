package com.github.unisay.moki

import java.io.{BufferedReader, InputStream, InputStreamReader, PrintStream}

import com.github.unisay.moki.TestService.TestService

import scala.collection.JavaConverters._
import org.slf4j.{Logger, LoggerFactory}

import scalaz.concurrent.Task

object Jvm {

  private lazy val logger: Logger = LoggerFactory.getLogger("Jvm")

  def testService(mainClass: String,
                  jvmArgs: List[String] = Nil,
                  programArgs: List[String] = Nil,
                  customClasspath: Option[String] = None,
                  output: PrintStream = System.out,
                  forceStop: Boolean = false): TestService[Process] =
    TestService(
      startTask = Task.delay {
        val maybeProcess = for {
          fileSeparator <- sys.props.get("file.separator")
          pathSeparator <- sys.props.get("path.separator")
          javaHome <- sys.props.get("java.home")
          javaPath = javaHome + fileSeparator + "bin" + fileSeparator + "java"
          cp <- sys.props.get("java.class.path").flatMap(path => customClasspath.map(path + pathSeparator + _))
        } yield {
          val arguments = (javaPath :: jvmArgs) ++ ("-cp" :: cp :: mainClass :: programArgs)
          val builder = new ProcessBuilder(arguments: _*)
          logger.debug("Starting process: " + builder.command().asScala.mkString(" "))
          val process = builder.start()
          new StreamGobbler(process.getInputStream, output).start()
          new StreamGobbler(process.getErrorStream, output).start()
          process
        }
        maybeProcess.getOrElse(throw new RuntimeException("Failed to initialize JVM process"))
      },
      stopTask = (process: Process) => Task.delay {
        if (process.isAlive) {
          if (forceStop) {
            logger.info("Forcibly destroying process...")
            val returnStatus = process.destroyForcibly().waitFor()
            logger.info("Process returned status: " + returnStatus)
          } else {
            logger.info("Destroying process...")
            process.destroy()
          }
        }
      }
  )

  private class StreamGobbler(val inputStream: InputStream, val printStream: PrintStream) extends Thread {
    setDaemon(true)
    override def run(): Unit = {
      new BufferedReader(new InputStreamReader(inputStream, "utf-8"))
        .lines()
        .forEach(printStream.println(_))
    }
  }
}
