package com.github.unisay.moki

import scalaz.concurrent.Task

trait Domain {

  type TestService[S] = ConfiguredTestService[Any, S]
  type ConfiguredTestService[I, S] = BaseTestService[I, S, S]

  sealed trait BaseTestService[-I, F, S] {
    def start: I => Task[S]
    def stop: S => Task[Unit]
  }

  trait InitialTestService[F, S] extends BaseTestService[Unit, F, S] { s1 =>
    def :>:[I2, F2, S2](s2: BaseTestService[I2, F2, S2]): BaseTestService[I2, F2 => F, Unit => S2] =
      new ComposableTestService[I2, F2 => F, Unit => S2] {
        def start: I2 => Task[Unit => S2] = i2 => s2.start(i2) map thunk
        def stop: (Unit => S2) => Task[Unit] = f => s2.stop(f(()))
      }
  }

  trait ComposableTestService[I, F, S] extends BaseTestService[I, F, S]

  def result[T]: InitialTestService[T, T] =
    new InitialTestService[T, T] {
      def start: Unit => Task[T] = sys.error("must not be called")
      def stop: T => Task[Unit] = ignoreArg(Task.now(()))
    }

  def ignoreArg[A, B](a: A): B => A = _ => a
  def thunk[A](a: A): Unit => A = ignoreArg(a)

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

object TestService {
  def apply[S](startTask: Task[S], stopTask: S => Task[Unit] = ignoreArg(Task.now(()))): TestService[S] =
    new ComposableTestService[Any, S, S] {
      def start: Any => Task[S] = ignoreArg(startTask)
      def stop: S => Task[Unit] = stopTask
    }
}

object ConfiguredTestService {
  def apply[I, S](startTask: I => Task[S],
                  stopTask: S => Task[Unit] = ignoreArg(Task.now(()))): ConfiguredTestService[I, S] =
    new ComposableTestService[I, S, S] {
      def start: I => Task[S] = startTask
      def stop: S => Task[Unit] = stopTask
    }
}

