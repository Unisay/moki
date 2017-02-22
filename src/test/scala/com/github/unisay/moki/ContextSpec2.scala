package com.github.unisay.moki

import com.github.unisay.moki.Fixture._
import org.scalatest.FlatSpec
import org.scalatest.MustMatchers.{a => _, _}

import scalaz._
import Scalaz._
import scala.collection.mutable.ListBuffer
import scalaz.concurrent.Task

object Fixture {

  trait A

  object a extends A {
    override def toString: String = "a"
  }

  trait B

  object b extends B {
    override def toString: String = "b"
  }

  trait C

  object c extends C {
    override def toString: String = "c"
  }

  trait D

  object d extends D {
    override def toString: String = "d"
  }

  trait E

  object e extends E {
    override def toString: String = "e"
  }

}

class ContextSpec extends FlatSpec {

  case class Service[R](start: Task[Resource[R]]) {
    def run[Z](f: R => Task[Z]): Task[Z] =
      for {
        r <- start
        z <- f(r.res)
        _ <- r.stop.attempt
      } yield z
  }

  object Service {

    implicit val monadService: Monad[Service] = new Monad[Service] {
      def bind[X, Y](starterX: Service[X])(xToStarterY: X => Service[Y]) =
        Service(
          start = for {
            resX <- starterX.start
            resY <- xToStarterY(resX.res).start
          } yield Resource[Y](res = resY.res, stop = (resY.stop.attempt >> resX.stop.attempt).void)
        )
      def point[X](x: => X) = Service(start = Task.now(Resource[X](res = x)))
    }
  }

  case class Resource[R](res: R, stop: Task[Unit] = Task.now(()))

  "composition" must "work" in {
    val logBuffer = ListBuffer[String]()
    def log(s: String): Unit = {
      println(s)
      logBuffer += s
    }

    val serviceA: Service[A] = Service(start = Task.delay {
        log("Starting resource A")
        Resource[A](res = a, stop = Task.delay(log("Stopping resource A")))
      })

    val serviceB: Service[B] = Service(start = Task.delay {
        log("Starting resource B")
        Resource[B](res = b, stop = Task.delay(log("Stopping resource B")))
      })

    val f: (A, B) => Task[C] = (a, b) => Task.delay {
      log(s"$a + $b = $c")
      c
    }

    val sequential =
      for {
        a <- serviceA
        b <- serviceB
      } yield (a, b)

    sequential.run(f.tupled).unsafePerformSync mustEqual c

    val parallel = (serviceA ⊛ serviceB).tupled

    logBuffer.clear()

    parallel.run(f.tupled).unsafePerformSync mustEqual c

    logBuffer must contain theSameElementsInOrderAs List(
      "Starting resource A",
      "Starting resource B",
      "a + b = c",
      "Stopping resource B",
      "Stopping resource A"
    )
  }

}
