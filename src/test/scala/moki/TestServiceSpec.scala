package moki

import moki.TestService.returning
import org.scalatest.{FlatSpec, MustMatchers}
import moki.Ev._

import scalaz.concurrent.Task

class TestServiceSpec extends FlatSpec with MustMatchers {

  behavior of "TestService"

  trait Db
  object Db extends Db
  trait Http
  object Http extends Http
  trait Email
  object Email extends Email

  def createTestService[S](name: String, state: S) = TestService(
    startTask = Task.delay { println(s"Starting $name with state = $state"); state },
    stopTask  = Task.delay { println(s"Stopping $name") })

  val dbService: TestService[Db, Db] = createTestService("DatabaseService", Db)
  val httpService: TestService[Http, Http] = createTestService("HttpService", Http)
  val emailService: TestService[Email, Email] = createTestService("EmailService", Email)

  private val service: TestService[
    Db => Http => Email => Unit,
    Unit => (Db, Unit => (Http, Unit => (Email, Unit => Unit))) // TODO: hide this type
  ] = dbService :>: httpService :>: emailService :>: returning[Unit]

  /*
  E: moki.Ev[Unit => (Db, Unit => (Http, Unit => (Email, Unit => Unit))),
             Db => (Http => (Email => Unit))]
   */
  it should "apply" in service {
    (dbClient: Db) => (httpClient: Http) => (emailClient: Email) => {
      dbClient mustEqual Db
      httpClient mustEqual Http
      emailClient mustEqual Email
    }
  }
}
