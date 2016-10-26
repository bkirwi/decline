package com.monovore.decline

import cats.{Applicative, Eval}
import cats.data.Validated
import cats.implicits._

private[decline] object Parse {

  type Result[A] = Validated[List[String], A]

  def success[A](a: A): Result[A] = Validated.valid(a)

  def failure[A](reasons: String*): Result[A] = Validated.invalid(reasons.toList)

  trait Accumulator[A] {
    def parseOption(remaining: List[String]): Option[(Accumulator[A], List[String])]
    def parseArgs(remaining: List[String]): (Accumulator[A], List[String])
    def get: Result[Eval[Result[A]]]
  }

  object Accumulator {

    val LongOpt = "--(.+)".r
    val LongEquals= "--([^=]+)=(.+)".r

    val zoom = Applicative[Result] compose Applicative[Eval] compose Applicative[Result]

    case class Pure[A](value: Result[A]) extends Accumulator[A] {
      override def parseOption(remaining: List[String]): Option[(Accumulator[A], List[String])] = None
      override def parseArgs(remaining: List[String]): (Accumulator[A], List[String]) = this -> remaining
      override def get: Result[Eval[Result[A]]] = success(Eval.now(value))
    }

    case class App[X, A](left: Accumulator[X => A], right: Accumulator[X]) extends Accumulator[A] {

      override def parseOption(remaining: List[String]): Option[(Accumulator[A], List[String])] = {
        def maybeLeft = left.parseOption(remaining).map { case (newLeft, rest) => App(newLeft, right) -> rest }
        def maybeRight = right.parseOption(remaining).map { case (newRight, rest) => App(left, newRight) -> rest }
        maybeLeft orElse maybeRight
      }

      override def parseArgs(remaining: List[String]): (Accumulator[A], List[String]) = {
        val (newLeft, rest0) = left.parseArgs(remaining)
        val (newRight, rest1) = right.parseArgs(rest0)
        App(newLeft, newRight) -> rest1
      }

      override def get: Result[Eval[Result[A]]] = zoom.map2(left.get, right.get) { (f, a) => f(a) }
    }

    case class Regular[A](longFlag: String, values: List[String] = Nil, read: List[String] => Result[A]) extends Accumulator[A] {

      override def parseOption(remaining: List[String]): Option[(Accumulator[A], List[String])] = remaining match {
        case LongOpt(`longFlag`) :: value :: rest => Some(copy(values = values :+ value) -> rest)
        case LongEquals(`longFlag`, value) :: rest => Some(copy(values = values :+ value) -> rest)
        case _ => None
      }

      override def parseArgs(remaining: List[String]): (Accumulator[A], List[String]) = this -> remaining

      override def get: Result[Eval[Result[A]]] = read(values).map { x => Eval.now(success(x)) }
    }

    case class Flag[A](longFlag: String, values: Int = 0, read: Int => Result[A]) extends Accumulator[A] {

      override def parseOption(remaining: List[String]): Option[(Accumulator[A], List[String])] = remaining match {
        case LongOpt(`longFlag`) :: rest => Some(copy(values = values + 1) -> rest)
        case _ => None
      }

      override def parseArgs(remaining: List[String]): (Accumulator[A], List[String]) = this -> remaining

      override def get: Result[Eval[Result[A]]] = read(values).map { x => Eval.now(success(x)) }
    }

    case class Argument[A](limit: Int, values: List[String] = Nil, read: List[String] => Result[A]) extends Accumulator[A] {

      override def parseOption(remaining: List[String]): Option[(Accumulator[A], List[String])] = None

      override def parseArgs(remaining: List[String]): (Accumulator[A], List[String]) = {
        val (taken, rest) = remaining.splitAt(limit)
        copy(values = taken) -> rest
      }

      override def get: Result[Eval[Result[A]]] = read(values).map { x => Eval.now(success(x)) }
    }

    case class Validate[A, B](a: Accumulator[A], f: A => Result[B]) extends Accumulator[B] {
      
      override def parseOption(remaining: List[String]): Option[(Accumulator[B], List[String])] =
        a.parseOption(remaining).map { case (acc, rest) => Validate(acc, f) -> rest }
      
      override def parseArgs(remaining: List[String]): (Accumulator[B], List[String]) = {
        val (a0, rest) = a.parseArgs(remaining)
        Validate(a0, f) -> rest
      }

      override def get: Result[Eval[Result[B]]] = a.get.map { _.flatMap { res => Eval.later(res andThen f) } }
    }

    implicit def applicative: Applicative[Accumulator] = new Applicative[Accumulator] {

      override def pure[A](x: A): Accumulator[A] =
        Pure(success(x))

      override def ap[A, B](ff: Accumulator[(A) => B])(fa: Accumulator[A]): Accumulator[B] =
        App(ff, fa)
    }

    def fromOpts[A](opts: Opts[A]): Accumulator[A] = opts match {
      case Opts.Pure(a) => Accumulator.Pure(Parse.success(a))
      case Opts.App(f, a) => Accumulator.App(fromOpts(f), fromOpts(a))
      case Opts.Validate(a, validation) => Validate(fromOpts(a), validation)
      case single: Opts.Single[_, A] => single.opt match {
        case Opt.Flag(name) => Flag(name, read = single.read)
        case Opt.Regular(name, _) => Regular(name, read = single.read)
        case Opt.Arguments(_, limit) => Argument(limit, read = single.read)
      }
    }

    def consume[A](args: List[String], accumulator: Accumulator[A]): Result[A] = {
      accumulator.parseOption(args)
        .map { case (next, rest) => consume(rest, next) }
        .getOrElse {

          val (postArgs, rest) = accumulator.parseArgs(args)

          if (rest.isEmpty) {
            postArgs.get.andThen { _.value }
          }
          else failure(s"Unrecognized arguments: ${rest.mkString(" ")}")
        }
    }
  }

  def run[A](args: List[String], opts: Opts[A]): Result[A] = {
    val start = Accumulator.fromOpts(opts)
    Accumulator.consume(args, start)
  }
}