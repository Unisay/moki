package com.github.unisay.moki

import com.github.unisay.moki.Moki.httpService
import fs2.interop.scalaz._
import org.http4s.client.blaze.SimpleHttp1Client
import org.http4s.dsl._
import org.scalatest.{Assertion, FlatSpec, MustMatchers}

import scala.concurrent.duration._
import scalaz.concurrent.Task

class MokiTest extends FlatSpec with MustMatchers {

  "Moki httpService" must "work as expected" in {
    httpService() :>: httpService() :>: httpService() :>: result[Task[Assertion]] runSync {
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

  "Dependent services" must "work as expected" in {
    val portProducer = TestService(Task.now(8080))
    val portConsumer = ConfiguredTestService((i: Int) => Task.delay(println(s"Port = $i")))

    portProducer :>: portConsumer :>: result[Assertion] runSync {
      port => _ => port mustEqual 8080
    }
  }

}
