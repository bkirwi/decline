package com.monovore.clique

import cats.syntax.all._
import cats.{Applicative, Foldable, Monad}

sealed trait FoldM[M[_], I, O] { self =>

  type S

  def begin: M[S]
  def step: (S, I) => M[S]
  def done: S => M[O]

  def foldOver[F[_]](foldable: F[I])(implicit M: Monad[M], F: Foldable[F]) =
    for {
      finalState <-
        F.foldLeft(foldable, begin) { (mState, next) =>
          for {
            state <- mState
            newState <- step(state, next)
          } yield newState
        }
      out <- done(finalState)
    } yield out
  
  def mapM[C](fn: O => M[C])(implicit M: Monad[M]) =
    new FoldM[M, I, C] {
      override type S = self.S
      override def begin: M[S] = self.begin
      override def step: (S, I) => M[S] = self.step
      override def done: (S) => M[C] =
      { state => self.done(state).flatMap(fn) }
    }
}

object FoldM {

  def over[I] = new Over[I]

  class Over[I] {
    def apply[M[_], O](i: M[O])(s: (O, I) => M[O])(implicit M: Monad[M]): FoldM[M, I, O] =
      new FoldM[M, I, O] {
        override type S = O
        override def begin: M[O] = i
        override def step: (O, I) => M[O] = s
        override def done: (O) => M[O] = M.pure
      }
  }

  implicit def applicative[M[_], I](implicit M: Monad[M]): Applicative[FoldM[M, I, ?]] =
    new Applicative[FoldM[M, I, ?]] {
      override def pure[A](a: A): FoldM[M, I, A] =
        FoldM.over[I](M.pure()) { (_, _) => M.pure() }
          .mapM { _ => M.pure(a)}

      override def ap[A, B](ff: FoldM[M, I, A => B])(fa: FoldM[M, I, A]): FoldM[M, I, B] = {
        val begin = (ff.begin |@| fa.begin).tupled

        val stepped =
          FoldM.over[I](begin) { (state, next) =>
            val (ffState, faState) = state
            (ff.step(ffState, next) |@| fa.step(faState, next)).tupled
          }

        stepped
          .mapM { case (ffState, faState) =>
            (ff.done(ffState) |@| fa.done(faState)).map { (f, a) => f(a) }
          }
      }
    }
}