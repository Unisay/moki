package com.github.unisay.moki

import java.io._

import scala.collection.JavaConverters._
import org.slf4j.{Logger, LoggerFactory}

import scalaz.concurrent.Task

trait ProcessService {

  private lazy val logger: Logger = LoggerFactory.getLogger("ProcessService")

  def processService(arguments: List[String] = Nil,
                     output: PrintStream = System.out,
                     forceStop: Boolean = false): TestService[Process] =
    TestService(
      startTask = startProcess(output)(arguments),
      stopTask = stopProcess(_: Process, forceStop))

  protected def startProcess(output: PrintStream)(arguments: List[String]): Task[Process] =
    Task.delay {
      val builder = new ProcessBuilder(arguments: _*)
      logger.debug("Starting process: " + builder.command().asScala.mkString(" "))
      val process = builder.start()
      new StreamGobbler(process.getInputStream, output).start()
      new StreamGobbler(process.getErrorStream, output).start()
      process
    }

  protected def stopProcess(process: Process, forceStop: Boolean): Task[Unit] =
    Task.delay {
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

  protected class StreamGobbler(val inputStream: InputStream, val printStream: PrintStream) extends Thread {
    setDaemon(true)
    override def run(): Unit = {
      try {
        val reader = new BufferedReader(new InputStreamReader(inputStream, "utf-8"))
        reader.lines().forEach(printStream.println(_))
      } catch {
        case io: IOException => logger.info(io.getMessage)
      }
    }
  }

}
