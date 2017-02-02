package moki

import scalaz.concurrent.Task
import moki.TestServices._
import org.scalatest.FlatSpec
import shapeless._

class TestServiceSpec extends FlatSpec {

  behavior of "TestService"

  def createTestService[N](name: String) = TestService(
    start = (i: N) => Task.delay { println(s"Starting $name: $i"); i.toString },
    stop  = (s: String) => Task.delay { println(s"Stopping $name: $s") })

  val foo: TestService[Int, String] = createTestService("Foo")
  val bar: TestService[Byte, String] = createTestService("Bar")
  val baz: TestService[Long, String] = createTestService("Baz")

  val services: TestService[Int :: Byte :: HNil, String :: String :: HNil] = foo :> bar
//  val services: TestService[Int :: Byte :: Long :: HNil, String :: String :: String :: HNil] = foo :> bar :> baz

  it should "apply" in {
    services.use { (i: Int, b: Byte) =>
      test
    }.unsafePerformSync
  }

  val test: (String, String) => Task[Unit] =
    (s1, s2) => Task.delay{ println(s"Using $s1 and $s2"); () }
}
