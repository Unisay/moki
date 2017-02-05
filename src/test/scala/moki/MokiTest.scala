package moki

import fs2.interop.scalaz._
import moki.TestService._
import org.http4s.client.blaze.SimpleHttp1Client
import org.scalatest.{Assertion, FlatSpec, MustMatchers}

import scala.concurrent.duration._
import scalaz.concurrent.Task

class MokiTest extends FlatSpec with MustMatchers {

  private val services =
    Moki.testService("localhost", 8080) :>:
    Moki.testService("localhost", 8081) :>:
    Moki.testService("localhost", 8082) :>:
    result[Task[Assertion]]

  "Moki server" must "notify about incoming request" in runSync(services) {
    mokiClient1 => mokiClient2 => mokiClient3 =>
        for {
          response  <- SimpleHttp1Client().expect[String]("http://localhost:8080")
          paths1    <- mokiClient1.requests.take(1).map(_.uri.path).runLog.timed(10.second)
          received2 <- mokiClient2.received
          received3 <- mokiClient3.received
        } yield {
          response mustEqual ""
          paths1 must contain theSameElementsAs Some("/")
          received2 mustEqual 0
          received3 mustEqual 0
        }
    }

}
