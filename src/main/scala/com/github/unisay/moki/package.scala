package com.github.unisay

import fs2.Task
import fs2.util.~>

import scalaz.concurrent.{Task => ZTask}

package object moki extends Domain with HttpTestService with JvmService with ProcessService {

  implicit class ScalazTaskToFs2[A](val tazk: ZTask[A]) extends AnyVal {
    def toFs2: Task[A] = Task.delay(tazk.unsafePerformSync)
  }

  implicit class Fs2TaskToScalaz[A](val task: Task[A]) extends AnyVal {
    def toScalaz: ZTask[A] = ZTask.delay(task.unsafeRun())
  }

  implicit val scalazToFs2: ZTask ~> Task = new (ZTask ~> Task) {
    def apply[A](f: ZTask[A]): Task[A] = f.toFs2
  }

}
