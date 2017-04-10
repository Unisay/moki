package com.github.unisay.moki

import fs2.{Strategy, Task}
import org.scalatest.{BeforeAndAfter, FlatSpec, MustMatchers}
import org.slf4j.LoggerFactory

import scala.collection.mutable.ListBuffer

@SuppressWarnings(Array("org.wartremover.warts.NonUnitStatements"))
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

  private val log = ListBuffer[String]()

  before(log.clear())

  def testService[S](name: String, state: S): TestService[S] = TestService(
    start = Task {
      logger.info(s"Starting $name with state = $state")
      log += s"on $name".toLowerCase
      state
    },
    stop  = (s: S) => Task {
      logger.info(s"Stopping $name for state $s")
      log += s"off $name".toLowerCase
    })

  def testServiceThatFailsToStart(name: String) = TestService(
    start = Task {
      log += s"on $name".toLowerCase
      logger.info(s"Exploding $name")
      if (true) sys.error("KABOOM!")
    },
    stop = (_: Any) => Task {
      logger.info(s"Stopping $name")
      log += s"off $name".toLowerCase
    })

  def testServiceThatFailsToStop(name: String) = TestService(
    start = Task {
      logger.info(s"Starting $name")
      log += s"on $name".toLowerCase
      ()
    },
    stop = (_: Unit) => Task {
      log += s"off $name".toLowerCase
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
      log += "action"
      logger.info("Started assertions...")
      Thread.sleep(3000)
      dbClient mustEqual Db
      httpClient mustEqual Http
      emailClient mustEqual Email
      logger.info("Finished assertions...")
    }.unsafeAttemptRun().isRight mustBe true

    log must contain theSameElementsInOrderAs List(
      "on database",
      "on http",
      "on email",
      "action",
      "off email",
      "off http",
      "off database"
    )
  }

  it should "tolerate failure on start" in {
    val test = for {
      _ <- testService("Database", Db)
      _ <- testService("Http", Http)
      _ <- testService("Email", Email)
      _ <- testServiceThatFailsToStart("Bad Citizen")
    } yield succeed

    test.run0.unsafeAttemptRun().isLeft mustBe true

    log must contain theSameElementsInOrderAs List(
      "on database",
      "on http",
      "on email",
      "on bad citizen",
      "off email",
      "off http",
      "off database"
    )
  }

  it should "tolerate failure in test" in {
    val env = for {
      _ <- testService("Database", Db)
      _ <- testService("Http", Http)
      _ <- testService("Email", Email)
    } yield ()

    env.run(_ => fail("kaboom!")).unsafeAttemptRun().isLeft mustBe true

    log must contain theSameElementsInOrderAs List(
      "on database",
      "on http",
      "on email",
      "off email",
      "off http",
      "off database"
    )
  }

  it should "tolerate failure on stop" in {
    val test = for {
      _ <- testService("Database", Db)
      _ <- testService("Http", Http)
      _ <- testService("Email", Email)
      _ <- testServiceThatFailsToStop("Bad Citizen")
    } yield succeed

    test.run0.unsafeAttemptRun().isRight mustBe true

    log must contain theSameElementsInOrderAs List(
      "on database",
      "on http",
      "on email",
      "on bad citizen",
      "off bad citizen",
      "off email",
      "off http",
      "off database"
    )
  }
}
