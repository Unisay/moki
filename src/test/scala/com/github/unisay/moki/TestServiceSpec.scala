package com.github.unisay.moki

import org.scalatest.{Assertion, FlatSpec, MustMatchers}
import org.slf4j.LoggerFactory
import fs2.Task

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
    startTask = Task.delay {
      logger.info(s"Starting $name with state = $state")
      startLog += name
      state
    },
    stopTask  = (s: S) => Task.delay {
      logger.info(s"Stopping $name for state $s")
      stopLog += name
    })

  private val env =
    testService("DatabaseService", Db) :>:
    testService("HttpService", Http)   :>:
    testService("EmailService", Email) :>:
    result[Assertion]

  it should "apply" in {
    env.runSync {
      dbClient => httpClient => emailClient => {
        logger.info("Doing assertions...")
        dbClient mustEqual Db
        httpClient mustEqual Http
        emailClient mustEqual Email
      }
    }
    val expectedStartLog = "DatabaseService" :: "HttpService" :: "EmailService" :: Nil
    startLog must contain theSameElementsInOrderAs expectedStartLog
    stopLog must contain theSameElementsInOrderAs expectedStartLog.reverse
  }
}
