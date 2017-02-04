package moki

import moki.TestService._
import org.http4s.client.blaze.SimpleHttp1Client
import org.scalatest.{Assertion, FlatSpec, MustMatchers}
import org.slf4j.LoggerFactory
import fs2.interop.scalaz._
import scala.concurrent.duration._
import scalaz.concurrent.Task

class MokiTest extends FlatSpec with MustMatchers {

  private val logger = LoggerFactory.getLogger(this.getClass)

  type Host = String
  type Port = Int

  def moki(host: Host, port: Port): TestService[MokiClient, MokiClient] =
    TestService(
      Task.delay(logger.info(s"Starting server ($host:$port) up")).flatMap(_ => Moki.startServer(host, port)),
      client => Task.delay(logger.info(s"Shutting server ($host:$port) down")).flatMap(_ => client.shutdownServer))

  private val services =
    moki("localhost", 8080) :>:
    moki("localhost", 8081) :>:
    moki("localhost", 8082) :>:
    result[Task[Assertion]]

  "Moki server" must "notify about incoming request" in runSync(services) {
    mokiClient1 => mokiClient2 => mokiClient3 =>
        for {
          response <- SimpleHttp1Client().expect[String]("http://localhost:8080")
          paths1 <- mokiClient1.requests.take(1).map(_.uri.path).runLog.timed(10.second)
//          paths2 <- mokiClient2.requests.take(1).map(_.uri.path).runLog.timed(100.millis)
//          paths3 <- mokiClient3.requests.take(1).map(_.uri.path).runLog.timed(100.millis)
        } yield {
          response mustEqual ""
          paths1 must contain theSameElementsAs Some("/")
//          paths2 must contain theSameElementsAs Some("/")
//          paths3 must contain theSameElementsAs Some("/")
        }
    }

}
