package moki

import org.scalatest.{FlatSpec, MustMatchers}

class MokiTest extends FlatSpec with MustMatchers {

  type Host = String
  type Port = Int

  "Moki server" must "notify about incoming request" in {}
  /*services.use {
    case ((c1: MokiClient, c2: MokiClient, c3: MokiClient)) =>
      for {
        response <- SimpleHttp1Client().expect[String]("http://localhost:8080")
        paths1 <- c1.requests.take(1).map(_.uri.path).runLog.timed(10.second)
        paths2 <- c2.requests.take(1).map(_.uri.path).runLog.timed(10.second)
        paths3 <- c3.requests.take(1).map(_.uri.path).runLog.timed(10.second)
      } yield {
        response mustEqual ""
        paths1 must contain theSameElementsAs Some("/")
        paths2 must contain theSameElementsAs Some("/")
        paths3 must contain theSameElementsAs Some("/")
      }
  }.unsafePerformSync*/

}
