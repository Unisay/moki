package moki

import moki.TestService._
import org.scalatest.{Assertion, FlatSpec, MustMatchers}
import moki.Ev._

import scalaz.concurrent.Task

class TestServiceSpec extends FlatSpec with MustMatchers {

  behavior of "TestService"

  trait Db { override def toString: String = "Db" }
  object Db extends Db
  trait Http { override def toString: String = "Http" }
  object Http extends Http
  trait Email { override def toString: String = "Email" }
  object Email extends Email
  trait Done
  object Done extends Done

  def createTestService[S](name: String, state: S) = TestService(
    startTask = Task.delay { println(s"Starting $name with state = $state"); state },
    stopTask  = Task.delay { println(s"Stopping $name") })

  val dbService: TestService[Db, Db] = createTestService("DatabaseService", Db)
  val httpService: TestService[Http, Http] = createTestService("HttpService", Http)
  val emailService: TestService[Email, Email] = createTestService("EmailService", Email)

  private val service = dbService :>: httpService :>: emailService :>: returning[Assertion]

  it should "apply" in service {
    dbClient => httpClient => emailClient => {
      dbClient mustEqual Db
      httpClient mustEqual Http
      emailClient mustEqual Email
    }
  }.unsafePerformSync
}
