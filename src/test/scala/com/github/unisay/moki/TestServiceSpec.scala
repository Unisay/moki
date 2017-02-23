package com.github.unisay.moki

import fs2.Task
import org.scalatest.{FlatSpec, MustMatchers}
import org.slf4j.LoggerFactory

import scala.collection.mutable.ListBuffer

class TestServiceSpec extends FlatSpec with MustMatchers {
  private val logger = LoggerFactory.getLogger(this.getClass)

  behavior of "TestService"

  trait Db { override def toString: String = "Db" }
  object Db extends Db
  trait Http { override def toString: String = "Http" }
  object Http extends Http
  trait Email { override def toString: String = "Email" }
  object Email extends Email

  private val startLog = ListBuffer[String]()
  private val stopLog = ListBuffer[String]()

  def testService[S](name: String, state: S) = TestService(
    start = Task.delay {
      logger.info(s"Starting $name with state = $state")
      startLog += name
      state
    },
    stop  = (s: S) => Task.delay {
      logger.info(s"Stopping $name for state $s")
      stopLog += name
    })

  it should "start and stop in proper order" in {
    val test = for {
      dbClient <- testService("DatabaseService", Db)
      httpClient <- testService("HttpTestService", Http)
      emailClient <- testService("EmailService", Email)
    } yield {
      logger.info("Doing assertions...")
      dbClient mustEqual Db
      httpClient mustEqual Http
      emailClient mustEqual Email
    }

    test.run0.unsafeRunSync()

    val expectedStartLog = "DatabaseService" :: "HttpTestService" :: "EmailService" :: Nil
    startLog must contain theSameElementsInOrderAs expectedStartLog
    stopLog must contain theSameElementsInOrderAs expectedStartLog.reverse
  }

  it should "tolerate failure on stop" in {
    val test = for {
      _ <- TestService(start = Task.now(()), stop = (_: Unit) => Task.fail(new RuntimeException("KABOOM!")))
      dbClient <- testService("DatabaseService", Db)
      httpClient <- testService("HttpTestService", Http)
      emailClient <- testService("EmailService", Email)
    } yield {
      logger.info("Doing assertions...")
      dbClient mustEqual Db
      httpClient mustEqual Http
      emailClient mustEqual Email
    }

    test.run0.unsafeRunSync()

    val expectedStartLog = "DatabaseService" :: "HttpTestService" :: "EmailService" :: Nil
    startLog must contain theSameElementsInOrderAs expectedStartLog
    stopLog must contain theSameElementsInOrderAs expectedStartLog.reverse
  }
}
