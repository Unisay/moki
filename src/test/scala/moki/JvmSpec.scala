package moki

import java.io.{ByteArrayOutputStream, PrintStream}

import TestService._
import org.scalatest.{Assertion, FlatSpec, MustMatchers}
import java.nio.charset.StandardCharsets.UTF_8

import org.scalatest.concurrent.Eventually
import org.scalatest.time._

class JvmSpec extends FlatSpec with MustMatchers with Eventually {

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(2, Seconds)), interval = scaled(Span(5, Millis)))

  private val outputStream = new ByteArrayOutputStream()
  private val scalaVersion = if (sys.props.getOrElse("version.number", "unknown").startsWith("2.12")) "2.12" else "2.11"

  private val testService = Jvm.testService(
    mainClass = TestApplication.getClass.getName.stripSuffix("$"),
    jvmArgs = List("-Xms64m", "-Xmx256m"),
    programArgs = List("Hello", "World"),
    customClasspath = Some(s"target/scala-$scalaVersion/test-classes"),
    output = new PrintStream(outputStream)
  )

  "Jvm.testService" must "start and stop" in runSync(testService :>: result[Assertion]) { _ =>
    eventually {
      val log = new String(outputStream.toByteArray, UTF_8)
      log must startWith("Test Application started with arguments: Hello World\nWorking...")
    }
  }

}
