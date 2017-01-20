package com.monovore.decline

import cats.Functor
import cats.data.NonEmptyList
import cats.implicits._
import com.monovore.decline.Opts.Name

case class Parser[+A](command: Command[A]) {

  import Parser._

  def apply(args: List[String]): Either[Help, A] =
    consumeAll(args, Accumulator.fromOpts(command.options))

  def map[B](fn: A => B): Parser[B] =
    Parser(command.copy(options = command.options.map(fn)))

  def mapResult[B](fn: A => Result[B]): Parser[B] =
    Parser(command.copy(options = command.options.mapValidated(fn)))

  private[this] val help = Help.fromCommand(command)

  private[this] def failure[A](reason: String*): Either[Help, A] = Left(help.withErrors(reason.toList))

  private[this] def fromOut[A](out: Result[A]): Either[Help, A] = out.get.value match {
    case Result.Return(value) => Right(value)
    case Result.Missing(stuff) => failure(stuff.map { _.message }.distinct: _*)
    case Result.Fail(messages) => failure(messages.distinct: _*)
  }

  private[this] def consumeAll(args: List[String], accumulator: Accumulator[A]): Either[Help, A] = args match {
    case LongOptWithEquals(option, value) :: rest => {
      accumulator.parseOption(Opts.LongName(option))
        .toRight(help.withErrors(s"Unexpected option: --$option" :: Nil))
        .flatMap {
          case MatchFlag(next) => failure(s"Got unexpected value for flag: --$option")
          case MatchOption(next) => consumeAll(rest, next(value))
        }
    }
    case LongOpt(option) :: rest =>
      accumulator.parseOption(Opts.LongName(option))
        .toRight(help.withErrors(s"Unexpected option: --$option" :: Nil))
        .flatMap {
          case MatchFlag(next) => consumeAll(rest, next)
          case MatchOption(next) => rest match {
            case Nil => failure(s"Missing value for option: --$option")
            case value :: rest0 => consumeAll(rest0, next(value))
          }
        }
    case "--" :: rest => consumeArgs(rest, accumulator)
    case ShortOpt(NonEmptyString(flag, tail)) :: rest => {

      def consumeShort(char: Char, tail: String, accumulator: Accumulator[A]): Either[Help, A] =
        accumulator.parseOption(Opts.ShortName(char))
          .toRight(help.withErrors(s"Unexpected option: -$char" :: Nil))
          .flatMap {
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
        .map { result =>
          fromOut(result)
            .flatMap { _(rest).left.map { _.withPrefix(List(command.name)) } }
        }
        .orElse {
          accumulator.parseArg(arg).map { consumeAll(rest, _) }
        }
        .getOrElse(failure(s"Unexpected argument: $arg"))
    case Nil => fromOut(accumulator.result)
  }

  private[this] def consumeArgs(args: List[String], accumulator: Accumulator[A]): Either[Help, A] = args match {
    case Nil => fromOut(accumulator.result)
    case arg :: rest => {
      accumulator.parseArg(arg)
        .map { next => consumeArgs(rest, next) }
        .getOrElse { failure(s"Unexpected argument: $arg")}
    }
  }
}

object Parser {

  sealed trait Match[+A]
  case class MatchFlag[A](next: A) extends Match[A]
  case class MatchOption[A](next: String => A) extends Match[A]

  object Match {
    implicit val functor: Functor[Match] = new Functor[Match] {
      override def map[A, B](fa: Match[A])(f: (A) => B): Match[B] = fa match {
        case MatchFlag(next) => MatchFlag(f(next))
        case MatchOption(next) => MatchOption(next andThen f)
      }
    }
  }

  trait Accumulator[+A] {
    def parseOption(name: Opts.Name): Option[Match[Accumulator[A]]]
    def parseArg(arg: String): Option[Accumulator[A]]
    def parseSub(command: String): Option[Result[Parser[A]]]
    def result: Result[A]
  }

  val LongOpt = "--(.+)".r
  val LongOptWithEquals= "--(.+?)=(.+)".r
  val ShortOpt = "-(.+)".r

  object NonEmptyString {
    def unapply(string: String): Option[(Char, String)] =
      if (string.isEmpty) None
      else Some(string.charAt(0) -> string.substring(1))
  }

  object Accumulator {

    case class Pure[A](value: Result[A]) extends Accumulator[A] {
      override def parseOption(name: Name) = None

      override def parseArg(arg: String) = None

      override def parseSub(command: String) = None

      override def result = value
    }

    case class App[X, A](left: Accumulator[X => A], right: Accumulator[X]) extends Accumulator[A] {

      override def parseOption(name: Opts.Name) = {
        val leftMatch = left.parseOption(name).map { _.map { App(_, right) } }
        val rightMatch = right.parseOption(name).map { _.map { App(left, _) } }

        leftMatch <+> rightMatch
      }

      override def parseArg(arg: String) =
        left.parseArg(arg).map { App(_, right) } orElse
          right.parseArg(arg).map { App(left, _) }

      override def parseSub(command: String) = {
        val leftSub =
          left.parseSub(command)
            .map { leftResult =>
              (leftResult |@| right.result)
                .map { (parser, value) => parser.map { _(value) } }
            }

        val rightSub =
          right.parseSub(command)
            .map { rightResult =>
              (left.result |@| rightResult)
                .map { (fn, parser) => parser.map { fn(_) } }
            }

        leftSub <+> rightSub
      }

      override def result = left.result ap right.result
    }

    case class OrElse[A](left: Accumulator[A], right: Accumulator[A]) extends Accumulator[A] {

      override def parseOption(name: Name) = left.parseOption(name) <+> right.parseOption(name)

      override def parseArg(arg: String): Option[Accumulator[A]] = {
        (left.parseArg(arg), right.parseArg(arg)) match {
          case (Some(newLeft), Some(newRight)) => Some(OrElse(newLeft, newRight))
          case (Some(newLeft), None) => Some(newLeft)
          case (None, Some(newRight)) => Some(newRight)
          case (None, None) => None
        }
      }

      override def parseSub(command: String) = left.parseSub(command) <+> right.parseSub(command)

      override def result = left.result <+> right.result
    }

    case class Regular(names: List[Opts.Name], visibility: Visibility, values: List[String] = Nil) extends Accumulator[NonEmptyList[String]] {

      override def parseOption(name: Opts.Name) =
        if (names contains name) Some(MatchOption { value => copy(values = value :: values) })
        else None

      override def parseArg(arg: String) = None

      override def parseSub(command: String) = None

      def result =
        NonEmptyList.fromList(values.reverse)
          .map(Result.success)
          .getOrElse(visibility match {
            case Visibility.Normal => Result.missingFlag(names.head)
            case _ => Result.missing
          })
    }

    case class Flag(names: List[Opts.Name], visibility: Visibility, values: Int = 0) extends Accumulator[NonEmptyList[Unit]] {

      override def parseOption(name: Opts.Name) =
        if (names contains name) Some(MatchFlag(copy(values = values + 1)))
        else None

      override def parseArg(arg: String) = None

      override def parseSub(command: String) = None

      def result =
        NonEmptyList.fromList(List.fill(values)(()))
          .map(Result.success)
          .getOrElse(visibility match {
            case Visibility.Normal => Result.missingFlag(names.head)
            case _ => Result.missing
          })
    }

    case class Argument(limit: Int, values: List[String] = Nil) extends Accumulator[NonEmptyList[String]] {

      override def parseOption(name: Opts.Name) = None

      override def parseArg(arg: String) =
        if (values.size < limit) Some(copy(values = arg :: values))
        else None

      override def parseSub(command: String) = None

      def result =
        NonEmptyList.fromList(values.reverse)
          .map(Result.success)
          .getOrElse(Result.missingArgument)
    }

    case class Subcommand[A](name: String, action: Parser[A]) extends Accumulator[A] {

      override def parseOption(name: Name) = None

      override def parseArg(arg: String) = None

      override def parseSub(command: String) =
        if (command == name) Some(Result.success(action)) else None

      override def result = Result.missingCommand(name)
    }

    case class Validate[A, B](a: Accumulator[A], f: A => Result[B]) extends Accumulator[B] {

      override def parseOption(name: Opts.Name) =
        a.parseOption(name).map { _.map { copy(_, f) } }

      override def parseArg(arg: String) =
        a.parseArg(arg).map { Validate(_, f) }

      override def parseSub(command: String) =
        a.parseSub(command).map { _.map { _.mapResult(f) } }

      override def result = a.result.andThen(f)
    }


    def repeated[A](opt: Opt[A]): Accumulator[NonEmptyList[A]] = opt match {
      case Opt.Regular(name, _, _, visibility) => Regular(name, visibility)
      case Opt.Flag(name, _, visibility) => Flag(name, visibility)
      case Opt.Argument(_) => Argument(Int.MaxValue)
    }

    def fromOpts[A](opts: Opts[A]): Accumulator[A] = opts match {
      case Opts.Pure(a) => Accumulator.Pure(a)
      case Opts.App(f, a) => Accumulator.App(fromOpts(f), fromOpts(a))
      case Opts.OrElse(a, b) => OrElse(fromOpts(a), fromOpts(b))
      case Opts.Validate(a, validation) => Validate(fromOpts(a), validation)
      case Opts.Subcommand(command) => Subcommand(command.name, Parser(command))
      case Opts.Single(opt) => opt match {
        case Opt.Regular(name, _, _, visibility) => Validate(Regular(name, visibility), { v: NonEmptyList[String] => Result.success(v.toList.last) })
        case Opt.Flag(name, _, visibility) => Validate(Flag(name, visibility), { v: NonEmptyList[Unit] => Result.success(v.toList.last) })
        case Opt.Argument(_) => Validate(Argument(1), { args: NonEmptyList[String] => Result.success(args.head)})
      }
      case Opts.Repeated(opt) => repeated(opt)
    }
  }
}