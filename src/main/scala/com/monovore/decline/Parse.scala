package com.monovore.decline

import cats.implicits._
import cats.{Applicative, Eval}

private[decline] object Parse {

  import Result._

  sealed trait OptionResult[+A]
  case object Unmatched extends OptionResult[Nothing]
  case class MatchFlag[A](next: Accumulator[A]) extends OptionResult[A]
  case class MatchOption[A](next: String => Accumulator[A]) extends OptionResult[A]
  case object Ambiguous extends OptionResult[Nothing]

  // Here's a somewhat crazy type! The outer Result holds errors from 'reading'
  // the options, and the inner Result holds errors from validating the
  // options; the separation is important because we don't want to start
  // running user code if we've failed to read an option! The Eval is in there
  // for just that reason -- we don't actually force the value until we've
  // validated that the basic structure is correct.
  type WrappedResult[+A] = Result[Eval[Result[A]]]

  val wrappedApplicative =
    Applicative[Result] compose Applicative[Eval] compose Applicative[Result]

  trait Accumulator[+A] {
    // Read a single option
    def parseOption(name: Opts.Name): OptionResult[A] = Unmatched
    def parseArg(arg: String): Option[Accumulator[A]] = None
    def result: WrappedResult[A]
  }

  object Accumulator {

    val LongOpt = "--(.+)".r
    val LongOptWithEquals= "--(.+?)=(.+)".r
    val ShortOpt = "-(.+)".r

    object NonEmptyString {
      def unapply(string: String): Option[(Char, String)] =
        if (string.isEmpty) None
        else Some(string.charAt(0) -> string.substring(1))
    }

    case class Pure[A](value: Result[A]) extends Accumulator[A] {
      override def result: WrappedResult[A] = value.map { a => Eval.now(success(a))}
    }

    case class App[X, A](left: Accumulator[X => A], right: Accumulator[X]) extends Accumulator[A] {

      override def parseOption(name: Opts.Name): OptionResult[A] = {
        (left.parseOption(name), right.parseOption(name)) match {
          case (Unmatched, Unmatched) => Unmatched
          case (Unmatched, MatchFlag(nextRight)) => MatchFlag(App(left, nextRight))
          case (Unmatched, MatchOption(nextRight)) => MatchOption { value => App(left, nextRight(value)) }
          case (MatchFlag(nextLeft), Unmatched) => MatchFlag(App(nextLeft, right))
          case (MatchOption(nextLeft), Unmatched) => MatchOption { value => App(nextLeft(value), right) }
          case _ => Ambiguous
        }
      }

      override def parseArg(arg: String): Option[Accumulator[A]] = {
        left.parseArg(arg).map { App(_, right) } orElse
          right.parseArg(arg).map { App(left, _) }
      }

      override def result: WrappedResult[A] = wrappedApplicative.ap(left.result)(right.result)
    }

    case class Regular[A](names: List[Opts.Name], values: List[String] = Nil, read: List[String] => Result[A]) extends Accumulator[A] {

      override def parseOption(name: Opts.Name): OptionResult[A] =
        if (names contains name) MatchOption { value => copy(values = values :+ value)}
        else Unmatched

      def result = read(values).map { x => Eval.now(success(x)) }
    }

    case class Flag[A](names: List[Opts.Name], values: Int = 0, read: Int => Result[A]) extends Accumulator[A] {

      override def parseOption(name: Opts.Name): OptionResult[A] =
        if (names contains name) MatchFlag(copy(values = values + 1))
        else Unmatched

      def result = read(values).map { x => Eval.now(success(x)) }
    }

    case class Argument[A](limit: Int, values: List[String] = Nil, read: List[String] => Result[A]) extends Accumulator[A] {

      override def parseOption(name: Opts.Name): OptionResult[A] = Unmatched

      override def parseArg(arg: String): Option[Accumulator[A]] =
        if (values.size < limit) Some(copy(values = values :+ arg))
        else None

      def result = read(values).map { x => Eval.now(success(x)) }
    }

    case class Validate[A, B](a: Accumulator[A], f: A => Result[B]) extends Accumulator[B] {

      override def parseOption(name: Opts.Name): OptionResult[B] =
        a.parseOption(name) match {
          case Unmatched => Unmatched
          case MatchFlag(next) => MatchFlag(copy(a = next))
          case MatchOption(next) => MatchOption { value => copy(a = next(value)) }
          case Ambiguous => Ambiguous
        }

      override def parseArg(arg: String): Option[Accumulator[B]] =
        a.parseArg(arg).map { Validate(_, f) }


      override def result: WrappedResult[B] = {
        a.result.map { _.flatMap { res => Eval.later(res andThen f) } }
      }
    }

    implicit def applicative: Applicative[Accumulator] = new Applicative[Accumulator] {

      override def pure[A](x: A): Accumulator[A] =
        Pure(success(x))

      override def ap[A, B](ff: Accumulator[(A) => B])(fa: Accumulator[A]): Accumulator[B] =
        App(ff, fa)
    }

    def fromOpts[A](opts: Opts[A]): Accumulator[A] = opts match {
      case Opts.Pure(a) => Accumulator.Pure(success(a))
      case Opts.App(f, a) => Accumulator.App(fromOpts(f), fromOpts(a))
      case Opts.Validate(a, validation) => Validate(fromOpts(a), validation)
      case single: Opts.Single[_, A] => single.opt match {
        case Opt.Flag(name) => Flag(name, read = single.read)
        case Opt.Regular(name, _) => Regular(name, read = single.read)
        case Opt.Arguments(_, limit) => Argument(limit, read = single.read)
      }
    }

    def consumeAll[A](args: List[String], accumulator: Accumulator[A]): Result[A] = args match {
      case LongOptWithEquals(option, value) :: rest => accumulator.parseOption(Opts.LongName(option)) match {
        case Unmatched => failure(s"Unexpected option: --$option")
        case Ambiguous => failure(s"Ambiguous option: --$option")
        case MatchFlag(next) => failure(s"Got unexpected value for flag: --$option")
        case MatchOption(next) => consumeAll(rest, next(value))
      }
      case LongOpt(option) :: rest => accumulator.parseOption(Opts.LongName(option)) match {
        case Unmatched => failure(s"Unexpected option: --$option")
        case Ambiguous => failure(s"Ambiguous option: --$option")
        case MatchFlag(next) => consumeAll(rest, next)
        case MatchOption(next) => rest match {
          case Nil => failure(s"Missing value for option: --$option")
          case value :: rest0 => consumeAll(rest0, next(value))
        }
      }
      case "--" :: rest => consumeArgs(rest, accumulator)
      case ShortOpt(NonEmptyString(flag, tail)) :: rest => {

        def consumeShort(char: Char, tail: String, accumulator: Accumulator[A]): Result[A] =
          accumulator.parseOption(Opts.ShortName(char)) match {
            case Unmatched => failure(s"Unexpected option: -$flag")
            case Ambiguous => failure(s"Ambiguous option: -$flag")
            case MatchFlag(next) => tail match {
              case "" => consumeAll(rest, next)
              case NonEmptyString(nextFlag, nextTail) => consumeShort(nextFlag, nextTail, next)
            }
            case MatchOption(next) => tail match {
              case "" => rest match {
                case Nil => failure(s"Missing value for option: -$flag")
                case value :: rest0 => consumeAll(rest0, next(value))
              }
              case _ => consumeAll(rest, next(tail))
            }
          }

        consumeShort(flag, tail, accumulator)
      }
      case arg :: rest =>
        accumulator.parseArg(arg)
          .map { consumeAll(rest, _) }
          .getOrElse(failure(s"Unexpected argument: $arg"))
      case Nil => accumulator.result.andThen { _.value }
    }

    def consumeArgs[A](args: List[String], accumulator: Accumulator[A]): Result[A] = args match {
      case Nil => accumulator.result.andThen { _.value }
      case arg :: rest => {
        accumulator.parseArg(arg)
          .map { next => consumeArgs(rest, next) }
          .getOrElse { failure(s"Unexpected argument: $arg")}
      }
    }
  }

  def apply[A](args: List[String], opts: Opts[A]): Result[A] = {
    val start = Accumulator.fromOpts(opts)
    Accumulator.consumeAll(args, start)
  }
}