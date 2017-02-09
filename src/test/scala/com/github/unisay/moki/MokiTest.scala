package com.github.unisay.moki

import fs2.interop.scalaz._
import com.github.unisay.moki.TestService._
import org.http4s.client.blaze.SimpleHttp1Client
import org.http4s.dsl._
import org.scalatest.{Assertion, FlatSpec, MustMatchers}

import scala.concurrent.duration._
import scalaz.concurrent.Task

class MokiTest extends FlatSpec with MustMatchers {

  "Moki httpService" must "work as expected" in {
    val services =
      Moki.httpService() :>:
      Moki.httpService() :>:
      Moki.httpService() :>:
      result[Task[Assertion]]

    runSync(services) {
      http1 => http2 => http3 => for {
        _         <- http1.respond(_ => Ok())
        response  <- SimpleHttp1Client().expect[String](http1.uri)
        paths1    <- http1.requests.take(1).map(_.uri.path).runLog.timed(10.second)
        received2 <- http2.received
        received3 <- http3.received
      } yield {
        response mustEqual ""
        paths1 must contain theSameElementsAs Some("/")
        received2 mustEqual 0
        received3 mustEqual 0
      }
    }
  }

}
