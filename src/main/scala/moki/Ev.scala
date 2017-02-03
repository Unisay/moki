package moki

trait Ev[P, C] {
  type Out
  def apply(p: P, c: C): Out
}

object Ev {
  type Aux[P, C, O] = Ev[P, C] { type Out = O }

  implicit def one[A, B]: Ev.Aux[Unit => A, A => B, B] =
    new Ev[Unit => A, A => B] {
      type Out = B
      def apply(p: Unit => A, c: A => B): B = c(p(()))
    }

  implicit def more[A, B, C](implicit E: Ev[B, C]): Ev.Aux[Unit => (A, B), A => C, E.Out] =
    new Ev[Unit => (A, B), A => C] {
      type Out = E.Out
      def apply(p: Unit => (A, B), c: A => C): E.Out = {
        val (a, b) = p(())
        E.apply(b, c(a))
      }
    }

  def ev[P, C](p: P, c: C)(implicit E: Ev[P, C]): E.Out = E(p, c)
}

