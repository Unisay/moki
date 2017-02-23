package com.github.unisay.moki

import fs2.{Stream, Task}

import scala.Function.const

trait Domain {

  class TestService[A] private(val start: Task[Resource[A]]) {

    def flatMap[B](f: A => TestService[B]): TestService[B] =
      new TestService(start = compose(start, f.andThen(_.start)).map(b => Resource[B](b.r)))

    private def compose[X, Y](tx: Task[Resource[X]], f: X => Task[Resource[Y]]): Task[Resource[Y]] =
      Stream.bracket(tx)(rx => Stream.eval(f(rx.r)), _.stop).runLast.map(_.get)

    def map[B](f: A => B): TestService[B] = flatMap(a => TestService.point(f(a)))

    def run0: Task[A] = for { resource <- start; _ <- resource.stop } yield resource.r

    def run[R](f: A => R): Task[R] = runT(f andThen Task.now)

    def runT[R](f: A => Task[R]): Task[R] = compose(start, f.andThen(_.map(Resource(_)))).map(_.r)
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
      new TestService(start.map(r => Resource(r, stop(r))))

    def point[B](b: => B): TestService[B] =
      TestService(start = Task.now(b))
  }

  case class Resource[R](r: R, stop: Task[Unit] = Task.now(()))
}
