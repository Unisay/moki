package com.github.unisay.moki

import fs2.Strategy
import org.scalatest.concurrent.Eventually
import org.scalatest.time._
import org.scalatest.{Assertion, FlatSpec, MustMatchers}

import scala.collection.mutable.ListBuffer
import scala.sys.process.{Process, ProcessLogger}

class JvmServiceSpec extends FlatSpec with MustMatchers with Eventually {

  private implicit val strategy: Strategy = Strategy.fromCachedDaemonPool("io-thread-pool")

  "JvmService.jvmService" must "start and stop" in (testService :>: result[Assertion]).runSync { _ =>
    eventually(log must contain allOf("Test Application started with arguments: Hello World", "Working..."))
  }

  private val log = ListBuffer[String]()

  private def cp(cs: Class[_]*): String = cs.map(_.getProtectionDomain.getCodeSource.getLocation.getPath).mkString(":")

  private val testService: TestService[Process] = jvmService(
    mainClass = TestApplication.getClass.getName.stripSuffix("$"),
    jvmArgs = List("-Xms64m", "-Xmx256m"),
    programArgs = List("Hello", "World"),
    customClasspath = Some(cp(TestApplication.getClass)),
    processLogger = ProcessLogger(log.append(_))
  )

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(50, Millis)))
}
