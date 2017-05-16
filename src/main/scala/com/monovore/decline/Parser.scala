package com.monovore.decline

import cats.Functor
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.implicits._
import com.monovore.decline.Opts.Name

import scala.annotation.tailrec

private[decline] case class Parser[+A](command: Command[A]) extends (List[String] => Either[Help, A]) {

  import Parser._

  def apply(args: List[String]): Either[Help, A] =
    consumeAll(args, Accumulator.fromOpts(command.options))

  private[this] val help = Help.fromCommand(command)

  private[this] def failure[A](reason: String*): Either[Help, A] = Left(help.withErrors(reason.toList))

  private[this] def fromResult[A](out: Result[A]): Either[Help, A] = out.get.value match {
    case Result.Return(value) => Right(value)
    case Result.Fail(stuff) => failure(stuff.map { _.message }.distinct: _*)
    case Result.Halt(messages) => failure(messages.distinct: _*)
  }

  @tailrec
  private[this] def consumeAll(args: List[String], accumulator: Accumulator[A]): Either[Help, A] = args match {
    case LongOptWithEquals(option, value) :: rest => {
      accumulator.parseOption(Opts.LongName(option)) match {
        case Some(MatchFlag(next)) => failure(s"Got unexpected value for flag: --$option")
        case Some(MatchAmbiguous) => failure(s"Ambiguous option/flag: --$option")
        case Some(MatchOption(next)) => consumeAll(rest, next(value))
        case None => Left(help.withErrors(s"Unexpected option: --$option" :: Nil))
      }
    }
    case LongOpt(option) :: rest =>
      accumulator.parseOption(Opts.LongName(option)) match {
        case Some(MatchFlag(next)) => consumeAll(rest, next)
        case Some(MatchAmbiguous) => failure(s"Ambiguous option/flag: --$option")
        case Some(MatchOption(next)) => rest match {
          case Nil => failure(s"Missing value for option: --$option")
          case value :: rest0 => consumeAll(rest0, next(value))
        }
        case None => Left(help.withErrors(s"Unexpected option: --$option" :: Nil))
      }
    case "--" :: rest => consumeArgs(rest, accumulator)
    case ShortOpt(NonEmptyString(flag, tail)) :: rest => {

      @tailrec
      def consumeShort(char: Char, tail: String, accumulator: Accumulator[A]): Either[Help, (List[String], Accumulator[A])] =
        accumulator.parseOption(Opts.ShortName(char)) match {
          case Some(MatchAmbiguous) => failure(s"Ambiguous option/flag: -$char")
          case Some(MatchFlag(next)) => tail match {
            case "" => Right(rest -> next)
            case NonEmptyString(nextFlag, nextTail) => consumeShort(nextFlag, nextTail, next)
          }
          case Some(MatchOption(next)) => tail match {
            case "" => rest match {
              case Nil => failure(s"Missing value for option: -$char")
              case value :: rest0 => Right(rest0 -> next(value))
            }
            case _ => Right(rest -> next(tail))
          }
          case None => Left(help.withErrors(s"Unexpected option: -$char" :: Nil))
        }

      consumeShort(flag, tail, accumulator) match {
        case Right((newRest, newAccumulator)) => consumeAll(newRest, newAccumulator)
        case Left(help) => Left(help)
      }
    }
    case arg :: rest =>
      accumulator.parseSub(arg)
        .map { result =>
          result(rest)
            .left.map { _.withPrefix(List(command.name)) }
            .flatMap(fromResult)
        } match {
          case Some(out) => out
          case None => Accumulator.flattenArg(accumulator.parseArg(arg)) match {
            case Some(next) => consumeAll(rest, next)
            case None => failure(s"Unexpected argument: $arg")
          }
        }
    case Nil => fromResult(accumulator.result)
  }

  private[this] def consumeArgs(args: List[String], accumulator: Accumulator[A]): Either[Help, A] = args match {
    case Nil => fromResult(accumulator.result)
    case arg :: rest => {
      Accumulator.flattenArg(accumulator.parseArg(arg))
        .map { next => consumeArgs(rest, next) }
        .getOrElse { failure(s"Unexpected argument: $arg")}
    }
  }
}

private[decline] object Parser {

  sealed trait Match[+A]
  case class MatchFlag[A](next: A) extends Match[A]
  case class MatchOption[A](next: String => A) extends Match[A]
  case object MatchAmbiguous extends Match[Nothing]

  object Match {
    implicit val functor: Functor[Match] = new Functor[Match] {
      override def map[A, B](fa: Match[A])(f: A => B): Match[B] = fa match {
        case MatchFlag(next) => MatchFlag(f(next))
        case MatchOption(next) => MatchOption(next andThen f)
        case MatchAmbiguous => MatchAmbiguous
      }
    }
  }

  sealed trait Accumulator[+A] {
    def parseOption(name: Opts.Name): Option[Match[Accumulator[A]]]
    def parseArg(arg: String): List[Option[Accumulator[A]]]
    def parseSub(command: String): Option[List[String] => Either[Help, Result[A]]]
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

    def flattenArg[A](result: List[Option[Accumulator[A]]]): Option[Accumulator[A]] = result match {
      case Nil => None
      case Some(acc) :: rest => flattenArg(rest) match {
        case Some(other) => Some(OrElse(acc, other))
        case None => Some(acc)
      }
      case None :: rest => flattenArg(rest)
    }

    case class Pure[A](value: Result[A]) extends Accumulator[A] {
      override def parseOption(name: Name) = None

      override def parseArg(arg: String) = List(None)

      override def parseSub(command: String) = None

      override def result = value
    }

    case class App[X, A](left: Accumulator[X => A], right: Accumulator[X]) extends Accumulator[A] {

      override def parseOption(name: Opts.Name) = {
        (left.parseOption(name), right.parseOption(name)) match {
          case (Some(leftMatch), None) => Some(leftMatch.map { App(_, right) })
          case (None, Some(rightMatch)) => Some(rightMatch.map { App(left, _) })
          case (None, None) => None
          case _ => Some(MatchAmbiguous)
        }
      }

      override def parseArg(arg: String) = {
        val lefts = left.parseArg(arg).map { _.map(App(_, right)) }
        val rights = right.parseArg(arg).map { _.map(App(left, _)) }
        for {
          nextLeft <- lefts
          nextRight <- rights
        } yield nextLeft orElse nextRight
      }

      override def parseSub(command: String) = {
        val leftSub =
          left.parseSub(command)
            .map { parser =>
              parser andThen { _.map { leftResult =>
                (leftResult |@| right.result).map { _ apply _ }
              }}
            }

        val rightSub =
          right.parseSub(command)
            .map { parser =>
              parser andThen { _.map { rightResult =>
                (left.result |@| rightResult).map { _ apply _ }
              }}
            }

        leftSub <+> rightSub
      }

      override def result = left.result ap right.result
    }

    case class OrElse[A](left: Accumulator[A], right: Accumulator[A]) extends Accumulator[A] {

      override def parseOption(name: Name) =
        (left.parseOption(name), right.parseOption(name)) match {
          case (Some(MatchFlag(l)), Some(MatchFlag(r))) => Some(MatchFlag(OrElse(l, r)))
          case (Some(MatchOption(l)), Some(MatchOption(r))) => Some(MatchOption { value => OrElse(l(value), r(value)) })
          case (Some(_), Some(_)) => Some(MatchAmbiguous)
          case (l @ Some(_), None) => l
          case (None, r @ Some(_)) => r
          case (None, None) => None
        }

      override def parseArg(arg: String) =
        left.parseArg(arg) ::: right.parseArg(arg)

      override def parseSub(command: String) = left.parseSub(command) <+> right.parseSub(command)

      override def result = left.result <+> right.result
    }

    case class Regular(names: List[Opts.Name], visibility: Visibility, values: List[String] = Nil) extends Accumulator[NonEmptyList[String]] {

      override def parseOption(name: Opts.Name) =
        if (names contains name) Some(MatchOption { value => copy(values = value :: values) })
        else None

      override def parseArg(arg: String) = List(None)

      override def parseSub(command: String) = None

      def result =
        NonEmptyList.fromList(values.reverse)
          .map(Result.success)
          .getOrElse(visibility match {
            case Visibility.Normal => Result.missingFlag(names.head)
            case _ => Result.fail
          })
    }

    case class Flag(names: List[Opts.Name], visibility: Visibility, values: Int = 0) extends Accumulator[NonEmptyList[Unit]] {

      override def parseOption(name: Opts.Name) =
        if (names contains name) Some(MatchFlag(copy(values = values + 1)))
        else None

      override def parseArg(arg: String) = List(None)

      override def parseSub(command: String) = None

      def result =
        NonEmptyList.fromList(List.fill(values)(()))
          .map(Result.success)
          .getOrElse(visibility match {
            case Visibility.Normal => Result.missingFlag(names.head)
            case _ => Result.fail
          })
    }

    case class Argument(remaining: Int, values: List[String] = Nil) extends Accumulator[NonEmptyList[String]] {

      override def parseOption(name: Opts.Name) = None

      override def parseArg(arg: String) =
        if (remaining > 0) {
          val withArg = Some(Argument(remaining - 1, arg :: values))
          // We 'require' the first arg; everything after is optional
          if (values.isEmpty) List(withArg)
          else List(withArg, None)
        }
        else List(None)

      override def parseSub(command: String) = None

      def result =
        NonEmptyList.fromList(values.reverse)
          .map(Result.success)
          .getOrElse(Result.missingArgument)
    }

    case class Subcommand[A](name: String, action: Parser[A]) extends Accumulator[A] {

      override def parseOption(name: Name) = None

      override def parseArg(arg: String) = List(None)

      override def parseSub(command: String) =
        if (command == name) Some(action andThen { _ map Result.success }) else None

      override def result = Result.missingCommand(name)
    }

    case class Validate[A, B](a: Accumulator[A], f: A => ValidatedNel[String, B]) extends Accumulator[B] {

      override def parseOption(name: Opts.Name) =
        a.parseOption(name).map { _.map { copy(_, f) } }

      override def parseArg(arg: String) =
        a.parseArg(arg).map { _.map(Validate(_, f)) }

      override def parseSub(command: String) =
        a.parseSub(command).map { _ andThen  { _.map { _ andThen (f andThen Result.fromValidated) } } }

      override def result = a.result.andThen(f andThen Result.fromValidated)
    }

    case class HelpFlag(a: Accumulator[Unit]) extends Accumulator[Nothing] {
      override def parseOption(name: Name): Option[Match[Accumulator[Nothing]]] =
        a.parseOption(name).map { _.map(HelpFlag) }

      override def parseArg(arg: String) =
        a.parseArg(arg).map(_.map(HelpFlag))

      override def parseSub(command: String) =
        a.parseSub(command).map { _.map { _.map { _ andThen { _ => Result.fail } } } }

      override def result: Result[Nothing] =
        a.result andThen { _ => Result.fail }
    }


    def repeated[A](opt: Opt[A]): Accumulator[NonEmptyList[A]] = opt match {
      case Opt.Regular(name, _, _, visibility) => Regular(name, visibility)
      case Opt.Flag(name, _, visibility) => Flag(name, visibility)
      case Opt.Argument(_) => Argument(Int.MaxValue)
    }

    def fromOpts[A](opts: Opts[A]): Accumulator[A] = opts match {
      case Opts.Pure(a) => Accumulator.Pure(Result.success(a))
      case Opts.Missing => Accumulator.Pure(Result.fail)
      case Opts.HelpFlag(a) => Accumulator.HelpFlag(fromOpts(a))
      case Opts.App(f, a) => Accumulator.App(fromOpts(f), fromOpts(a))
      case Opts.OrElse(a, b) => OrElse(fromOpts(a), fromOpts(b))
      case Opts.Validate(a, validation) => Validate(fromOpts(a), validation)
      case Opts.Subcommand(command) => Subcommand(command.name, Parser(command))
      case Opts.Single(opt) => opt match {
        case Opt.Regular(name, _, _, visibility) => Validate(Regular(name, visibility), { v: NonEmptyList[String] => Validated.valid(v.toList.last) })
        case Opt.Flag(name, _, visibility) => Validate(Flag(name, visibility), { v: NonEmptyList[Unit] => Validated.valid(v.toList.last) })
        case Opt.Argument(_) => Validate(Argument(1), { args: NonEmptyList[String] => Validated.valid(args.head)})
      }
      case Opts.Repeated(opt) => repeated(opt)
    }
  }
}
