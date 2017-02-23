package com.github.unisay.moki

import fs2.Task

import scala.Function.const

trait Domain {

  class TestService[A] private(val start: Task[Resource[A]]) {

    def flatMap[B](f: A => TestService[B]): TestService[B] =
      new TestService(
        start = for {
          resourceA <- start
          resourceB <- f(resourceA.r).start
          stop = for { _ <- resourceB.stop.attempt; _ <- resourceA.stop.attempt } yield ()
        } yield Resource[B](resourceB.r, stop)
      )

    def map[B](f: A => B): TestService[B] =
      flatMap(a => TestService.point(f(a)))

    def run0: Task[A] = for { resource <- start; _ <- resource.stop } yield resource.r

    def run[R](f: A => R): Task[R] = runT(f andThen Task.now)

    def runT[R](f: A => Task[R]): Task[R] =
      for {
        resource <- start
        z <- f(resource.r)
        _ <- resource.stop.attempt
      } yield z
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

  case class Resource[R](r: R, stop: Task[Unit])
}
