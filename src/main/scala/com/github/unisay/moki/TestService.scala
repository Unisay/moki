package com.github.unisay.moki

import com.github.unisay.moki.TestService._
import fs2.Stream
import fs2.interop.scalaz._

import scalaz.Scalaz._
import scalaz.concurrent.Task

sealed trait BaseTestService[F, S] {
  def start: Task[S]
  def stop: S => Task[Unit]
}

trait InitialTestService[F, S] extends BaseTestService[F, S] { s1 =>
  def within[G, T](s2: BaseTestService[G, T]): ComposableTestService[G => F, Unit => T] = :>:(s2)
  def :>:[G, T](s2: BaseTestService[G, T]): ComposableTestService[G => F, Unit => T] =
    new ComposableTestService[G => F, Unit => T] {
      def start: Task[Unit => T] = s2.start map thunk
      def stop: (Unit => T) => Task[Unit] = f => s2.stop(f(()))
    }
}

trait ComposableTestService[F, S] extends BaseTestService[F, S] { s1 =>
  def within[G, T](s2: BaseTestService[G, T]): ComposableTestService[G => F, Unit => (T, S)] = :>:(s2)
  def :>:[G, T](s2: BaseTestService[G, T]): ComposableTestService[G => F, Unit => (T, S)] =
    new ComposableTestService[G => F, Unit => (T, S)] {
      def start: Task[Unit => (T, S)] = (s2.start |@| s1.start)((a, b) => thunk(a -> b))
      def stop: (Unit => (T, S)) => Task[Unit] = f => f(()) match { case (t, s) => s1.stop(s) >> s2.stop(t) }
    }
}

object TestService {
  type TestService[S] = BaseTestService[S, S]

  def apply[S0](startTask: Task[S0], stopTask: S0 => Task[Unit]): TestService[S0] =
    new ComposableTestService[S0, S0] {
      def start: Task[S0] = startTask
      def stop: S0 => Task[Unit] = stopTask
    }

  def result[T]: InitialTestService[T, T] =
    new InitialTestService[T, T] {
      def start: Task[T] = sys.error("must not be called")
      def stop: T => Task[Unit] = ignore(Task.now(()))
    }

  def run[F, S](services: BaseTestService[F, S])(f: F)(implicit A: Applicator[S, F]): Task[A.Out] =
    Stream.bracket(services.start)(s => Stream.eval(applyT(s, f)), services.stop).runLast.map(_.get)

  def runSync[F, S](services: BaseTestService[F, S])(f: F)(implicit A: Applicator[S, F]): A.Out =
    run(services)(f).unsafePerformSync

  def ignore[A, B](a: A): B => A = _ => a
  def thunk[A](a: A): Unit => A = ignore(a)

  trait Applicator[P, C] {
    type Out
    def apply(p: P, c: C): Task[Out]
  }

  // Takes precedence, don't remove!
  implicit def baseT[A, B]: Applicator[Unit => A, A => Task[B]] =
    new Applicator[Unit => A, A => Task[B]] {
      type Out = B
      def apply(p: Unit => A, c: A => Task[B]): Task[Out] = c(p(()))
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
}
