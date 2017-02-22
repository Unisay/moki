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

  trait Starter[I, R] {
    def start: Task[Resource[R]]
  }

  object Starter {

    def run[I, X, Z](starter: Starter[I, X]): Task[X] =
      for {
       resX <- starter.start
       _ <- resX.stop.attempt
      } yield resX.res

    implicit def monadStarter[I, R]: Monad[Starter[I, ?]] = new Monad[Starter[I, ?]] {
      def bind[X, Y](starterA: Starter[I, X])(f: (X) => Starter[I, Y]) = new Starter[I, Y] {
        def start: Task[Resource[Y]] = for {
          resX <- starterA.start
          resY <- f(resX.res).start
        } yield new Resource[Y] {
          def res: Y = resY.res
          def stop: Task[Unit] = resY.stop.attempt >> resX.stop
        }
      }

      def point[X](x: => X) = new Starter[I, X] {
        def start: Task[Resource[X]] =
          Task.now(new Resource[X] {
            def res: X = x
            def stop: Task[Unit] = Task.now(())
          })
      }
    }
  }

  trait Resource[R] {
    def res: R
    def stop: Task[Unit]
  }

  object Resource {
    implicit def monadResource[I]: Monad[Resource] = new Monad[Resource] {
      def bind[X, Y](fa: Resource[X])(f: (X) => Resource[Y]): Resource[Y] = new Resource[Y] {
        val me: Resource[Y] = f(fa.res)
        def res: Y = me.res
        def stop: Task[Unit] = for { _ <- me.stop.attempt; _ <- fa.stop.attempt } yield ()
      }
      def point[X](x: => X): Resource[X] = new Resource[X] {
        def res = x
        def stop: Task[Unit] = Task.now(())
      }
    }
  }



  "composition" must "work" in {
    val logBuffer = ListBuffer[String]()
    def log(s: String): Unit = logBuffer += s

    val starterA: Starter[Unit, A] = new Starter[Unit, A] {
      def start = Task.delay {
        log("Starting resource A")
        new Resource[A] {
          def res: A = a
          def stop: Task[Unit] = Task.delay(log("Stopping resource A"))
        }
      }
    }
    val starterB: Starter[Unit, B] = new Starter[Unit, B] {
      def start = Task.delay {
        log("Starting resource B")
        new Resource[B] {
          def res: B = b
          def stop: Task[Unit] = Task.delay(log("Stopping resource B"))
        }
      }
    }


    val f: (A, B) => C = (a, b) => {
      log(s"$a + $b = $c")
      c
    }

    val env: Starter[Unit, C] =
      for {
        a <- starterA
        b <- starterB
      } yield f(a, b)

    Starter.run(env).unsafePerformSync mustEqual c

    logBuffer must contain theSameElementsInOrderAs List (
      "Starting resource A",
      "Starting resource B",
      "a + b = c",
      "Stopping resource B",
      "Stopping resource A"
    )

    /*
    ListBuffer("Starting resource A", "Starting resource B", "Stopping resource B", "Stopping resource A", "a + b = c")
    did not contain the same elements in the same (iterated) order as
    List("Starting resource A", "Starting resource B", "a + b = c", "Stopping resource B", "Stopping resource A")
    */
  }

}
