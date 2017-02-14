package com.github.unisay

import fs2.util.~>
import fs2.{Stream, Task}

import scalaz.concurrent.{Task => ZTask}

package object moki extends Domain with HttpTestService with JvmService with ProcessService {

  implicit class BaseTestServiceOps[I, F, S](val service: BaseTestService[I, F, S]) extends AnyVal {
    def :>:[I2, F2, S2 <: I](sup: BaseTestService[I2, F2, S2]): BaseTestService[I2, F2 => F, Unit => (S2, S)] =
      new ComposableTestService[I2, F2 => F, Unit => (S2, S)] {
        def start: I2 => Task[Unit => (S2, S)] =
          i2 => for {i <- sup.start(i2); s <- service.start(i) } yield thunk(i -> s)
        def stop: (Unit => (S2, S)) => Task[Unit] =
          f => f(()) match { case (s2, s) => service.stop(s) flatMap ignoreArg(sup.stop(s2)) }
      }

    def run(f: F)(implicit A: Applicator[S, F]): I => Task[A.Out] =
      (i: I) => Stream.bracket(service.start(i))(s => Stream.eval(applyT(s, f)), service.stop).runLast.map(_.get)
  }

  implicit class BaseTestServiceOpsU[U >: Unit, F, S](val service: BaseTestService[U, F, S]) extends AnyVal {
    def runSync[A >: Unit](f: F)(implicit A: Applicator[S, F]): A.Out = service.run(f)(A)(()).unsafeRun()
  }

  implicit class ScalazTaskToFs2[A](val ztask: ZTask[A]) extends AnyVal {
    def toFs2: Task[A] = Task.delay(ztask.unsafePerformSync)
  }

  implicit class Fs2TaskToScalaz[A](val task: Task[A]) extends AnyVal {
    def toScalaz: ZTask[A] = ZTask.delay(task.unsafeRun())
  }

  implicit val scalazToFs2: ZTask ~> Task = new (ZTask ~> Task) {
    def apply[A](f: ZTask[A]): Task[A] = f.toFs2
  }
}
