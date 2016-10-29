package com.monovore.decline

import cats.{Applicative, Eval}
import cats.data.Validated
import cats.implicits._

private[decline] object Parse {

  type Result[A] = Validated[List[String], A]

  def success[A](a: A): Result[A] = Validated.valid(a)

  def failure[A](reasons: String*): Result[A] = Validated.invalid(reasons.toList)

  sealed trait OptionResult[+A]
  case object Unmatched extends OptionResult[Nothing]
  case class Okay[A](next: Accumulator[A]) extends OptionResult[A]
  case class More[A](next: String => Accumulator[A]) extends OptionResult[A]
  case object Ambiguous extends OptionResult[Nothing]

  // Here's a somewhat crazy type! The outer Result holds errors from 'reading'
  // the options, and the inner Result holds errors from validating the
  // options; the separation is important because we don't want to start
  // running user code if we've failed to read an option! The Eval is in there
  // for just that reason -- we don't actually force the value until we've
  // validated that the basic structure is correct.
  type WrappedResult[+A] = Result[Eval[Result[A]]]

  trait Accumulator[+A] {
    // Read a single option
    def parseOption(option: String): OptionResult[A]
    // Read _all_ the positional arguments
    def parseArgs(remaining: List[String]): (WrappedResult[A], List[String])
  }

  object Accumulator {

    val LongOpt = "--(.+)".r
    val LongOptWithEquals= "--(.+?)=(.+)".r

    val zoom = Applicative[Result] compose Applicative[Eval] compose Applicative[Result]

    case class Pure[A](value: Result[A]) extends Accumulator[A] {
      override def parseOption(remaining: String): OptionResult[A] = Unmatched
      override def parseArgs(remaining: List[String]): (WrappedResult[A], List[String]) = success(Eval.now(value)) -> remaining
    }

    case class App[X, A](left: Accumulator[X => A], right: Accumulator[X]) extends Accumulator[A] {

      override def parseOption(remaining: String): OptionResult[A] = {
        (left.parseOption(remaining), right.parseOption(remaining)) match {
          case (Unmatched, Unmatched) => Unmatched
          case (Unmatched, Okay(nextRight)) => Okay(App(left, nextRight))
          case (Unmatched, More(nextRight)) => More { value => App(left, nextRight(value)) }
          case (Okay(nextLeft), Unmatched) => Okay(App(nextLeft, right))
          case (More(nextLeft), Unmatched) => More { value => App(nextLeft(value), right) }
          case _ => Ambiguous
        }
      }

      override def parseArgs(remaining: List[String]): (WrappedResult[A], List[String]) = {
        val (newLeft, rest0) = left.parseArgs(remaining)
        val (newRight, rest1) = right.parseArgs(rest0)
        zoom.map2(newLeft, newRight) { (f, a) => f(a) } -> rest1
      }
    }

    case class Regular[A](longFlag: String, values: List[String] = Nil, read: List[String] => Result[A]) extends Accumulator[A] {

      override def parseOption(remaining: String): OptionResult[A] =
        if (remaining == longFlag) More { value => copy(values = values :+ value)}
        else Unmatched

      override def parseArgs(remaining: List[String]): (WrappedResult[A], List[String]) =
        read(values).map { x => Eval.now(success(x)) } -> remaining
    }

    case class Flag[A](longFlag: String, values: Int = 0, read: Int => Result[A]) extends Accumulator[A] {

      override def parseOption(remaining: String): OptionResult[A] =
        if (remaining == longFlag) Okay(copy(values = values + 1))
        else Unmatched

      override def parseArgs(remaining: List[String]): (WrappedResult[A], List[String]) =
        read(values).map { x => Eval.now(success(x)) } -> remaining
    }

    case class Argument[A](limit: Int, values: List[String] = Nil, read: List[String] => Result[A]) extends Accumulator[A] {

      override def parseOption(remaining: String): OptionResult[A] = Unmatched

      override def parseArgs(remaining: List[String]): (WrappedResult[A], List[String]) = {
        val (taken, rest) = remaining.splitAt(limit)
        read(taken).map { x => Eval.now(success(x)) } -> rest
      }
    }

    case class Validate[A, B](a: Accumulator[A], f: A => Result[B]) extends Accumulator[B] {

      override def parseOption(remaining: String): OptionResult[B] =
        a.parseOption(remaining) match {
          case Unmatched => Unmatched
          case Okay(next) => Okay(copy(a = next))
          case More(next) => More { value => copy(a = next(value)) }
          case Ambiguous => Ambiguous
        }

      override def parseArgs(remaining: List[String]): (WrappedResult[B], List[String]) = {
        val (a0, rest) = a.parseArgs(remaining)
        val validates = a0.map { _.flatMap { res => Eval.later(res andThen f) } }
        validates -> rest
      }
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

    def consumeAll[A](args: List[String], accumulator: Accumulator[A]): Result[A] = args match {
      case LongOptWithEquals(option, value) :: rest => accumulator.parseOption(option) match {
        case Unmatched => failure(s"Unexpected option: --$option")
        case Ambiguous => failure(s"Ambiguous option: --$option")
        case Okay(next) => failure(s"Got unexpected value for flag: --$option")
        case More(next) => consumeAll(rest, next(value))
      }
      case LongOpt(option) :: rest => accumulator.parseOption(option) match {
        case Unmatched => failure(s"Unexpected option: --$option")
        case Ambiguous => failure(s"Ambiguous option: --$option")
        case Okay(next) => consumeAll(rest, next)
        case More(next) => rest match {
          case Nil => failure(s"Missing value for option: --$option")
          case value :: rest0 => consumeAll(rest0, next(value))
        }
      }
      case "--" :: rest => consumeArgs(rest, accumulator)
      case _ => consumeArgs(args, accumulator)
    }

    def consumeArgs[A](args: List[String], accumulator: Accumulator[A]) = {
      val (wrappedResult, rest) = accumulator.parseArgs(args)
      if (rest.isEmpty) wrappedResult.andThen { _.value }
      else failure(s"Unrecognized arguments: ${rest.mkString(" ")}")
    }
  }

  def run[A](args: List[String], opts: Opts[A]): Result[A] = {
    val start = Accumulator.fromOpts(opts)
    Accumulator.consumeAll(args, start)
  }
}