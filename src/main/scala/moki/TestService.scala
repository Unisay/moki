package moki

import moki.Ev._

import scalaz.Scalaz._
import scalaz.concurrent.Task

sealed trait TestService[F, S] {
  def start: Task[S]
  def stop: Task[Unit]
  def apply(f: F)(implicit E: Ev[S, F]): Task[E.Out] =
    for {
      s <- start
      out = ev(s, f)
      _ <- stop
    } yield out

  protected def thunk[A](a: A): Unit => A = _ => a
}

trait TestService1[F, S] extends TestService[F, S] { s1 =>
  def :>:[G, T](s2: TestService[G, T]): TestService2[G => F, Unit => (T, Unit => S)] =
    new TestService2[G => F, Unit => (T, Unit => S)] {
      def start: Task[Unit => (T, Unit => S)] = (s2.start |@| s1.start)((a, b) => thunk(a, thunk(b)))
      def stop: Task[Unit] = s2.stop >>= thunk(s1.stop)
    }
}

trait TestService2[F, S] extends TestService[F, S] { s1 =>
  def :>:[G, T](s2: TestService[G, T]): TestService2[G => F, Unit => (T, S)] =
    new TestService2[G => F, Unit => (T, S)] {
      def start: Task[Unit => (T, S)] = (s2.start |@| s1.start)((a, b) => thunk((a, b)))
      def stop: Task[Unit] = s2.stop >>= thunk(s1.stop)
    }
}

object TestService {

  def apply[S0](startTask: Task[S0], stopTask: Task[Unit]): TestService[S0, S0] = new TestService1[S0, S0] {
    def start: Task[S0] = startTask
    def stop: Task[Unit] = stopTask
  }

  def returning[T]: TestService1[T, Unit] = new TestService1[T, Unit] {
    def stop = Task.now(())
    def start = ???
  }
}

