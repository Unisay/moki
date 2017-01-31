package moki

import fs2.interop.scalaz._
import org.http4s.client.blaze.SimpleHttp1Client
import org.scalatest.{FlatSpec, MustMatchers}
import scala.concurrent.duration._

class MokiTest extends FlatSpec with MustMatchers {

  "Moki server" must "notify about incoming request" in (
    for {
      client <- Moki.startServer
      response <- SimpleHttp1Client().expect[String]("http://localhost:8080")
      requestPaths <- client.requests.take(1).map(_.uri.path).runLog.timed(10.second)
      _ <- client.shutdownServer
    } yield {
      response mustEqual ""
      requestPaths must contain theSameElementsAs Some("/")
    }
  ).unsafePerformSync

}
