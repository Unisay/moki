package moki

import org.scalatest.{FlatSpec, MustMatchers}

import scalaz.concurrent.Task

class InductiveApplicationSpec extends FlatSpec with MustMatchers {

  trait Applicator[P, C] {
    type Out
    def apply(p: P, c: C): Task[Out]
  }

  implicit def base[A, B]: Applicator[Unit => A, A => B] =
    new Applicator[Unit => A, A => B] {
      type Out = B
      def apply(p: Unit => A, c: A => B): Task[Out] = Task.delay(c(p(())))
    }

  implicit def step[A, B, C](implicit BC: Applicator[B, C]): Applicator[Unit => (A, B), A => C] =
    new Applicator[Unit => (A, B), A => C] {
      type Out = BC.Out
      def apply(p: Unit => (A, B), c: A => C): Task[Out] = p(()) match { case (a, b) => BC(b, c(a)) }
    }

  def applyT[P, C](p: P, c: C)(implicit applicator: Applicator[P, C]): Task[applicator.Out] = applicator(p, c)

  "single application" must "return task" in {
    val producer: Unit => Int = thunk(42)
    val consumer: Int => String = (i: Int) => i.toString
    applyT(producer, consumer).unsafePerformSync mustEqual "42"
  }

  "inductive application" must "return task" in {
    type Producer = Unit => (Int, Unit => (Char, Unit => Symbol))
    type Consumer = Int => Char => Symbol => String

    val producer: Producer = thunk(42 -> thunk('c' -> thunk('s)))
    val consumer: Consumer = i => c => s => s"$i $c $s"

    applyT(producer, consumer).unsafePerformSync mustEqual "42 c 's"
  }

  def ignore[A, B](a: A): B => A = _ => a
  def thunk[A](a: A): Unit => A = ignore(a)
}
