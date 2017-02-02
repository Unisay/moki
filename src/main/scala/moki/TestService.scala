package moki

import scalaz._
import scalaz.Scalaz._
import scalaz.concurrent.Task

object TestServices {

  type For[A, B] = Kleisli[Task, A, B]

  case class TestService[I, S](start: I For S, stop: S For Unit) {
    def use[R](in: I, use: S => Task[R]): Task[R] = apply(Kleisli(use)).run(in)
    def apply[R](use: S For R): I For R = start >=> (use |@| stop).tupled.map(_._1)
    def >>[I2, S2](other: TestService[I2, S2]): TestService[(I, I2), (S, S2)] = // TODO: order
      TestService[(I, I2), (S, S2)](zip(start, other.start), zip(stop, other.stop).void)
    def zip[A, B, C, D](l: A For B, r: C For D): (A, C) For (B, D) =
      (l.local[(A, C)](_._1) |@| r.local[(A, C)](_._2)).tupled
  }

  object TestService {
    def apply[A, B](start: A => Task[B], stop: B => Task[Unit]): TestService[A, B] =
      TestService(Kleisli(start), Kleisli(stop))
  }
}

