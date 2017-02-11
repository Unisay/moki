package com.github.unisay.moki

import java.io.{ByteArrayOutputStream, PrintStream}

import TestService._
import org.scalatest.{Assertion, FlatSpec, MustMatchers}
import java.nio.charset.StandardCharsets.UTF_8

import org.scalatest.concurrent.Eventually
import org.scalatest.time._

class JvmServiceSpec extends FlatSpec with MustMatchers with Eventually {

  "Jvm.jvmService" must "start and stop" in runSync(testService :>: result[Assertion]) { _ =>
    eventually(log must startWith("Test Application started with arguments: Hello World\nWorking..."))
  }

  private val outputStream = new ByteArrayOutputStream()

  private def log: String = new String(outputStream.toByteArray, UTF_8)

  private def cp(cs: Class[_]*): String = cs.map(_.getProtectionDomain.getCodeSource.getLocation.getPath).mkString(":")

  private val testService = Moki.jvmService(
    mainClass = TestApplication.getClass.getName.stripSuffix("$"),
    jvmArgs = List("-Xms64m", "-Xmx256m"),
    programArgs = List("Hello", "World"),
    customClasspath = Some(cp(TestApplication.getClass)),
    output = new PrintStream(outputStream)
  )

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(10, Seconds)), interval = scaled(Span(50, Millis)))
}
