package com.github.unisay.moki

import fs2.{Strategy, Task}
import org.scalatest.{BeforeAndAfter, FlatSpec, MustMatchers}
import org.slf4j.LoggerFactory

import scala.collection.mutable.ListBuffer

class TestServiceSpec extends FlatSpec with MustMatchers with BeforeAndAfter {
  private val logger = LoggerFactory.getLogger(this.getClass)
  private implicit val strategy: Strategy = Strategy.fromCachedDaemonPool("io")

  behavior of "TestService"

  trait Db { override def toString: String = "Db" }
  object Db extends Db
  trait Http { override def toString: String = "Http" }
  object Http extends Http
  trait Email { override def toString: String = "Email" }
  object Email extends Email

  private val startLog = ListBuffer[String]()
  private val stopLog = ListBuffer[String]()

  before {
    startLog.clear()
    stopLog.clear()
  }

  def testService[S](name: String, state: S): TestService[S] = TestService(
    start = Task {
      logger.info(s"Starting $name with state = $state")
      startLog += name
      state
    },
    stop  = (s: S) => Task {
      logger.info(s"Stopping $name for state $s")
      stopLog += name
    })

  def testServiceThatFailsToStart(name: String) = TestService(
    start = Task {
      startLog += name
      logger.info(s"Exploding $name")
      if (true) sys.error("KABOOM!")
    },
    stop = (_: Any) => Task {
      logger.info(s"Stopping $name")
      stopLog += name
    })

  def testServiceThatFailsToStop(name: String) = TestService(
    start = Task {
      logger.info(s"Starting $name")
      startLog += name
      ()
    },
    stop = (_: Unit) => Task {
      stopLog += name
      logger.info(s"Exploding $name")
      sys.error("KABOOM!")
    })

  it should "start and stop in proper order" in {
    val env = for {
      dbClient <- testService("Database", Db)
      httpClient <- testService("Http", Http)
      emailClient <- testService("Email", Email)
    } yield (dbClient, httpClient, emailClient)

    env.run { case (dbClient, httpClient, emailClient) =>
      Thread.sleep(3000)
      logger.info("Doing assertions...")
      dbClient mustEqual Db
      httpClient mustEqual Http
      emailClient mustEqual Email
    }.unsafeAttemptRun().isRight mustBe true

    val expectedStartLog = "Database" :: "Http" :: "Email" :: Nil
    startLog must contain theSameElementsInOrderAs expectedStartLog
    stopLog must contain theSameElementsInOrderAs expectedStartLog.reverse
  }

  it should "tolerate failure on start" in {
    val test = for {
      _ <- testService("Database", Db)
      _ <- testService("Http", Http)
      _ <- testService("Email", Email)
      _ <- testServiceThatFailsToStart("BadCitizen")
    } yield succeed

    test.run0.unsafeAttemptRun().isLeft mustBe true

    startLog must contain theSameElementsInOrderAs "Database" :: "Http" :: "Email" :: "BadCitizen" :: Nil
    stopLog must contain theSameElementsInOrderAs "Email" :: "Http" :: "Database" :: Nil
  }

  it should "tolerate failure in test" in {
    val test = for {
      _ <- testService("Database", Db)
      _ <- testService("Http", Http)
      _ <- testService("Email", Email)
    } yield fail("kaboom!") : Unit

    test.run0.unsafeAttemptRun().isLeft mustBe true

    startLog must contain theSameElementsInOrderAs "Database" :: "Http" :: "Email" :: Nil
    stopLog must contain theSameElementsInOrderAs "Email" :: "Http" :: "Database" :: Nil
  }

  it should "tolerate failure on stop" in {
    val test = for {
      _ <- testService("Database", Db)
      _ <- testService("Http", Http)
      _ <- testService("Email", Email)
      _ <- testServiceThatFailsToStop("BadCitizen")
    } yield succeed

    test.run0.unsafeAttemptRun().isLeft mustBe true

    startLog must contain theSameElementsInOrderAs "Database" :: "Http" :: "Email" :: "BadCitizen" :: Nil
    stopLog must contain theSameElementsInOrderAs "BadCitizen" :: "Email" :: "Http" :: "Database" :: Nil
  }
}
