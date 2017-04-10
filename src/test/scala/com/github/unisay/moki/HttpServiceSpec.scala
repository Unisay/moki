package com.github.unisay.moki

import fs2.{Strategy, Task}
import org.http4s.client.blaze.SimpleHttp1Client
import org.http4s.dsl._
import org.scalatest.{FlatSpec, MustMatchers}

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
class HttpServiceSpec extends FlatSpec with MustMatchers {

  private implicit val strategy: Strategy = Strategy.fromCachedDaemonPool("io-thread-pool")

  "HttpTestService httpService" must "work as expected" in {
    for {
      http1 <- httpService()
      http2 <- httpService()
      http3 <- httpService()
    } yield for {
      _         <- http1.respond(_ => Ok())
      response  <- SimpleHttp1Client().expect[String](http1.uri)
      paths1    <- http1.requests.take(1).map(_.uri.path).runLog
      received2 <- http2.received
      received3 <- http3.received
    } yield {
      response mustEqual ""
      paths1 must contain theSameElementsAs Some("/")
      received2 mustEqual 0
      received3 mustEqual 0
    }
  }

  "Dependent services" must "work as expected" in {
    val portProducer = TestService.point(8080)
    val portConsumer = (i: Int) => TestService[Unit](Task(println(s"Port = $i")))

    val assertion = for {
      port <- portProducer
      _ <- portConsumer(port)
    } yield port.mustEqual(8080)

    assertion.run0.unsafeRun()
  }

}
