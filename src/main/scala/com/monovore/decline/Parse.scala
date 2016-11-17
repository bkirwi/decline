package com.monovore.decline

import cats.Applicative
import cats.data.NonEmptyList
import cats.data.Validated.{Invalid, Valid}
import cats.implicits._
import com.monovore.decline.Opts.Name

private[decline] object Parse {

  import Result._

  sealed trait OptionResult[+A]
  case object Unmatched extends OptionResult[Nothing]
  case class MatchFlag[A](next: Accumulator[A]) extends OptionResult[A]
  case class MatchOption[A](next: String => Accumulator[A]) extends OptionResult[A]
  case object Ambiguous extends OptionResult[Nothing]

  trait Accumulator[+A] {
    def parseOption(name: Opts.Name): OptionResult[A]
    def parseArg(arg: String): Option[Accumulator[A]]
    def parseSub(command: String): Option[Accumulator[A]]
    def result: Result[A]
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
      override def parseOption(name: Name): OptionResult[A] = Unmatched

      override def parseArg(arg: String): Option[Accumulator[A]] = None

      override def parseSub(command: String): Option[Accumulator[A]] = None

      override def result: Result[A] = value
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

      override def parseSub(command: String): Option[Accumulator[A]] =
        left.parseSub(command).map { App(_, Pure(right.result)) } orElse
          right.parseSub(command).map { App(Pure(left.result), _) }

      override def result: Result[A] = Applicative[Result].ap(left.result)(right.result)
    }

    case class OrElse[A](left: Accumulator[A], right: Accumulator[A]) extends Accumulator[A] {

      override def parseOption(name: Name): OptionResult[A] = {
        (left.parseOption(name), right.parseOption(name)) match {
          case (Unmatched, Unmatched) => Unmatched
          case (Unmatched, MatchFlag(nextRight)) => MatchFlag(nextRight)
          case (Unmatched, MatchOption(nextRight)) => MatchOption { value => nextRight(value) }
          case (MatchFlag(nextLeft), Unmatched) => MatchFlag(nextLeft)
          case (MatchOption(nextLeft), Unmatched) => MatchOption { value => nextLeft(value) }
          case _ => Ambiguous
        }
      }

      override def parseArg(arg: String): Option[Accumulator[A]] = {
        (left.parseArg(arg), right.parseArg(arg)) match {
          case (Some(newLeft), Some(newRight)) => Some(OrElse(newLeft, newRight))
          case (Some(newLeft), None) => Some(newLeft)
          case (None, Some(newRight)) => Some(newRight)
          case (None, None) => None
        }
      }

      override def parseSub(command: String): Option[Accumulator[A]] = {
        left.parseSub(command) orElse right.parseSub(command)
      }

      override def result: Result[A] = left.result orElse right.result
    }

    case class Regular(names: List[Opts.Name], values: List[String] = Nil) extends Accumulator[NonEmptyList[String]] {

      override def parseOption(name: Opts.Name) =
        if (names contains name) MatchOption { value => copy(values = value :: values)}
        else Unmatched

      override def parseArg(arg: String) = None

      override def parseSub(command: String) = None

      def result =
        NonEmptyList.fromList(values.reverse)
          .map(Result.success)
          .getOrElse(Result.failure(s"Missing required option: ${names.head}"))
    }

    case class Flag(names: List[Opts.Name], values: Int = 0) extends Accumulator[NonEmptyList[Unit]] {

      override def parseOption(name: Opts.Name) =
        if (names contains name) MatchFlag(copy(values = values + 1))
        else Unmatched

      override def parseArg(arg: String) = None

      override def parseSub(command: String) = None

      def result =
        NonEmptyList.fromList(List.fill(values)(()))
          .map(Result.success)
          .getOrElse(Result.failure(s"Missing required option: ${names.head}"))
    }

    case class Argument(limit: Int, values: List[String] = Nil) extends Accumulator[NonEmptyList[String]] {

      override def parseOption(name: Opts.Name) = Unmatched

      override def parseArg(arg: String) =
        if (values.size < limit) Some(copy(values = arg :: values))
        else None

      override def parseSub(command: String) = None

      def result =
        NonEmptyList.fromList(values.reverse)
          .map(Result.success)
          .getOrElse(Result.failure(s"Expected an argument!"))
    }

    case class Subcommand[A](name: String, action: Accumulator[A]) extends Accumulator[A] {

      override def parseOption(name: Name): OptionResult[A] = Unmatched

      override def parseArg(arg: String): Option[Accumulator[A]] = None

      override def parseSub(command: String): Option[Accumulator[A]] =
        if (command == name) Some(action) else None

      override def result = failure(s"Expected command: $name")
    }

    case class Validate[A, B](a: Accumulator[A], f: A => Result[B]) extends Accumulator[B] {

      override def parseOption(name: Opts.Name) =
        a.parseOption(name) match {
          case Unmatched => Unmatched
          case MatchFlag(next) => MatchFlag(copy(a = next))
          case MatchOption(next) => MatchOption { value => copy(a = next(value)) }
          case Ambiguous => Ambiguous
        }

      override def parseArg(arg: String) =
        a.parseArg(arg).map { Validate(_, f) }

      override def parseSub(command: String) =
        a.parseSub(command).map { Validate(_, f) }

      override def result = a.result.andThen(f)
    }


    def repeated[A](opt: Opt[A]): Accumulator[NonEmptyList[A]] = opt match {
      case Opt.Regular(name, _, _) => Regular(name)
      case Opt.Flag(name, _) => Flag(name)
      case Opt.Argument(_) => Argument(Int.MaxValue)
    }

    def fromOpts[A](opts: Opts[A]): Accumulator[A] = opts match {
      case Opts.Pure(a) => Accumulator.Pure(a)
      case Opts.App(f, a) => Accumulator.App(fromOpts(f), fromOpts(a))
      case Opts.OrElse(a, b) => OrElse(fromOpts(a), fromOpts(b))
      case Opts.Validate(a, validation) => Validate(fromOpts(a), validation)
      case Opts.Subcommand(command) => Subcommand(command.name, fromOpts(command.options))
      case Opts.Single(opt) => opt match {
        case Opt.Regular(name, _, _) => Validate(Regular(name), { v: NonEmptyList[String] => Result.success(v.toList.last) })
        case Opt.Flag(name, _) => Validate(Flag(name), { v: NonEmptyList[Unit] => Result.success(v.toList.last) })
        case Opt.Argument(_) => Validate(Argument(1), { args: NonEmptyList[String] => Result.success(args.head)})
      }
      case Opts.Repeated(opt) => repeated(opt)
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
        accumulator.parseSub(arg)
          .map { next => consumeAll(rest, next) }
          .orElse {
            accumulator.parseArg(arg).map { consumeAll(rest, _) }
          }
          .getOrElse(failure(s"Unexpected argument: $arg"))
      case Nil => accumulator.result
    }

    def consumeArgs[A](args: List[String], accumulator: Accumulator[A]): Result[A] = args match {
      case Nil => accumulator.result
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