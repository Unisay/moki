package com.github.unisay.moki

import fs2.Stream.bracket
import fs2.{Stream, Task}

import scala.Function.const
import scalaz.syntax.bind._
import fs2.interop.scalaz._

trait Domain {

  class TestService[A] private(val start: Task[Res[A]]) {

    def flatMap[B](f: A => TestService[B]): TestService[B] =
      new TestService(
        start = {
          val init: Task[(Res[A], Either[Throwable, Res[B]])] = for {
            ra <- start
            attemptRb <- f(ra.r).start.attempt
          } yield (ra, attemptRb)
          val use: ((Res[A], Either[Throwable, Res[B]])) => Task[Res[B]] = {
            case (_, Left(throwable)) => Task.fail(throwable)
            case (resA, Right(resB)) => Task.now(Res[B](resB.r, (resB.stop.attempt >> resA.stop.attempt).void))
          }
          val stop: ((Res[A], Either[Throwable, Res[B]])) => Task[Unit] = {
            case (resA, Left(_)) => resA.stop
            case _ => Task.now(())
          }
          within(init)(use, stop)
        }
      )

    def map[B](f: A => B): TestService[B] = flatMap(a => TestService.point(f(a)))

    def run0: Task[A] = for { resource <- start; _ <- resource.stop } yield resource.r

    def run[R](f: A => R): Task[R] = runT(f andThen Task.now)

    def runT[R](f: A => Task[R]): Task[R] = within(start)(ra => f(ra.r), _.stop)

    private def within[X,Y,Z](start: Task[X])(use: X => Task[Y], stop: X => Task[Unit]): Task[Y] =
      bracket(start)(x => Stream.eval(use(x)), stop).runLast.map(_.head)
  }

  object TestService {

    /**
      * Create a test service by defining its life-cycle methods
      *
      * @param start possibly effectful computation that initializes resource R
      * @param stop possibly effectful computation that shuts test service down using resource
      * @tparam R resource provided by the test service
      * @return created test service
      */
    def apply[R](start: Task[R], stop: R => Task[Unit] = const(Task.now(()))(_: R)): TestService[R] =
      new TestService(start.map(r => Res(r, stop(r))))

    def point[B](b: => B): TestService[B] =
      TestService(start = Task.now(b))
  }

  case class Res[R](r: R, stop: Task[Unit] = Task.now(()))
}
