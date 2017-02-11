package com.github.unisay

import scalaz.concurrent.Task
import fs2.Stream
import fs2.interop.scalaz._
import scalaz.syntax.monad._

package object moki extends Domain {

  implicit class BaseTestServiceOps[I, F, S](val service: BaseTestService[I, F, S]) extends AnyVal {
    def :>:[I2, F2, S2 <: I](sup: BaseTestService[I2, F2, S2]): BaseTestService[I2, F2 => F, Unit => (S2, S)] =
      new ComposableTestService[I2, F2 => F, Unit => (S2, S)] {
        def start: I2 => Task[Unit => (S2, S)] =
          i2 => for {i <- sup.start(i2); s <- service.start(i) } yield thunk(i -> s)
        def stop: (Unit => (S2, S)) => Task[Unit] =
          f => f(()) match { case (s2, s) => sup.stop(s2) >> service.stop(s) }
      }

    def run(f: F)(implicit A: Applicator[S, F]): I => Task[A.Out] =
      (i: I) => Stream.bracket(service.start(i))(s => Stream.eval(applyT(s, f)), service.stop).runLast.map(_.get)
  }

  implicit class BaseTestServiceOpsU[U >: Unit, F, S](val service: BaseTestService[U, F, S]) extends AnyVal {
    def runSync[A >: Unit](f: F)(implicit A: Applicator[S, F]): A.Out = service.run(f)(A)(()).unsafePerformSync
  }
}
