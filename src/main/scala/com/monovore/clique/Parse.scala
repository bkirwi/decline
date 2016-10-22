package com.monovore.clique

import cats.Applicative
import cats.arrow.FunctionK
import cats.data.Validated
import cats.instances.all._
import cats.syntax.all._

object Parse {

  type Result[A] = Validated[List[String], A]

  def success[A](a: A): Result[A] = Validated.valid(a)

  def failure[A](reason: String): Result[A] = Validated.invalid(List(reason))

  trait Accumulator[A] {
    def consume(remaining: List[String]): Option[(Accumulator[A], List[String])]
    def get: Result[A]
  }

  object Accumulator {

    case class Pure[A](get: Result[A]) extends Accumulator[A] {
      override def consume(remaining: List[String]): Option[(Accumulator[A], List[String])] = None
    }

    case class App[X, A](left: Accumulator[X => A], right: Accumulator[X]) extends Accumulator[A] {

      override def consume(remaining: List[String]): Option[(Accumulator[A], List[String])] = {
        def maybeLeft = left.consume(remaining).map { case (newLeft, rest) => App(newLeft, right) -> rest }
        def maybeRight = right.consume(remaining).map { case (newRight, rest) => App(left, newRight) -> rest }
        maybeLeft orElse maybeRight
      }

      override def get: Result[A] = (left.get |@| right.get).map { (f, a) => f(a) }
    }

    case class Regular[A](longFlag: String, parser: List[String] => Result[A], values: List[String] = Nil) extends Accumulator[A] {

      val LongOpt = "--(.+)".r

      override def consume(remaining: List[String]): Option[(Accumulator[A], List[String])] = remaining match {
        case LongOpt(`longFlag`) :: value :: rest => Some(copy(values = values :+ value) -> rest)
        case _ => None
      }

      override def get: Result[A] = parser(values)
    }

    case class Flag[A](longFlag: String, parser: Int => Result[A], values: Int = 0) extends Accumulator[A] {

      val LongOpt = "--(.+)".r

      override def consume(remaining: List[String]): Option[(Accumulator[A], List[String])] = remaining match {
        case LongOpt(`longFlag`) :: rest => Some(copy(values = values + 1) -> rest)
        case _ => None
      }

      override def get: Result[A] = parser(values)
    }

    implicit def applicative: Applicative[Accumulator] = new Applicative[Accumulator] {

      override def pure[A](x: A): Accumulator[A] =
        Pure(success(x))

      override def ap[A, B](ff: Accumulator[(A) => B])(fa: Accumulator[A]): Accumulator[B] =
        App(ff, fa)
    }

    def fromOpts[A](opts: Opts[A]): Accumulator[A] = opts.value.foldMap(new FunctionK[Opt, Accumulator] {
      override def apply[A](fa: Opt[A]): Accumulator[A] = fa match {
        case Opt.Regular(name, _, reader) => Regular(name, reader)
        case Opt.Flag(name, reader) => Flag(name, reader)
      }
    })

    def consume[A](args: List[String], accumulator: Accumulator[A]): Result[A] = {
      accumulator.consume(args)
        .map { case (next, rest) => consume(rest, next) }
        .getOrElse {
          if (args.isEmpty) accumulator.get
          else failure(s"Unrecognized arguments: ${args.mkString(" ")}")
        }
    }
  }

  def run[A](args: List[String], opts: Opts[A]): Result[A] = {
    val start = Accumulator.fromOpts(opts)
    Accumulator.consume(args, start)
  }
}