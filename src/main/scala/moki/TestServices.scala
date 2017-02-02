package moki

import shapeless._

import scalaz._
import scalaz.Scalaz._
import scalaz.concurrent.Task

object TestServices {

  type For[A, B] = Kleisli[Task, A, B]

  case class TestService[I, S](start: I For S, stop: S For Unit) {
    def use[R]: I => S => Task[R] = in => apply(Kleisli(use)).run(in)

    def apply[R](use: S For R): I For R = start >=> (use |@| stop).tupled.map(_._1)

    def :>[I2, S2](other: TestService[I2, S2]): TestService[I :: I2 :: HNil, S :: S2 :: HNil] =
      TestService[I :: I2 :: HNil, S :: S2 :: HNil](
        start = (start.local[I :: I2 :: HNil](_.head) |@| other.start.local[I :: I2 :: HNil](_.last)).apply(_ :: _ :: HNil),
        stop = (other.stop.local[S :: S2 :: HNil](_.last) |@| stop.local[S :: S2 :: HNil](_.head)).apply(_ :: _ :: HNil).void)
  }

  object TestService {
    def apply[A, B](start: A => Task[B], stop: B => Task[Unit]): TestService[A, B] =
      TestService(Kleisli(start), Kleisli(stop))
  }
}

