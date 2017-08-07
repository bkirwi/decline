package com.monovore.decline

import cats.Functor
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import cats.implicits._
import com.monovore.decline.Opts.Name

import scala.annotation.tailrec
import scala.util.{Left, Right}

private[decline] case class Parser[+A](command: Command[A]) extends (List[String] => Either[Help, A]) {

  import Parser._

  def apply(args: List[String]): Either[Help, A] =
    consumeAll(args, Accumulator.fromOpts(command.options))

  private[this] val help = Help.fromCommand(command)

  private[this] def failure[A](reason: String*): Either[Help, A] = Left(help.withErrors(reason.toList))

  private[this] def evalResult[A](out: Result[A]): Either[Help, A] = out.get match {
    case Invalid(failed) => failure(failed.messages.distinct: _*)
    // NB: if any of the user-provided functions have side-effects, they will happen here!
    case Valid(fn) => fn() match {
      case Invalid(messages) => failure(messages.distinct: _*)
      case Valid(result) => Right(result)
    }
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
            .flatMap(evalResult)
        } match {
          case Some(out) => out
          case None => accumulator.parseArg(arg).toOption match {
            case Some(next) => consumeAll(rest, next)
            case None => failure(s"Unexpected argument: $arg")
          }
        }
    case Nil => evalResult(accumulator.result)
  }

  @tailrec
  private[this] def consumeArgs(args: List[String], accumulator: Accumulator[A]): Either[Help, A] = args match {
    case Nil => evalResult(accumulator.result)
    case arg :: rest => {
      accumulator.parseArg(arg).toOption match {
        case Some(next) => consumeArgs(rest, next)
        case None => failure(s"Unexpected argument: $arg")
      }
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

  type ArgOut[+A] = Either[Accumulator[A], Accumulator[A]]
  type Err[+A] = Validated[List[String], A]

  sealed trait Accumulator[+A] {
    def parseOption(name: Opts.Name): Option[Match[Accumulator[A]]]
    def parseArg(arg: String): ArgOut[A] = Left(this)
    def parseSub(command: String): Option[List[String] => Either[Help, Result[A]]]
    def result: Result[A]

    def mapValidated[B](fn: A => Err[B]): Accumulator[B] =
      Accumulator.Validate(this, fn)

    final def map[B](fn: A => B): Accumulator[B] = mapValidated(fn andThen Validated.valid)
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

      override def parseSub(command: String) = None

      override def result = value

      override def mapValidated[B](fn: (A) => Err[B]): Accumulator[B] = Pure(value.mapValidated(fn))
    }

    def ap[A, B](left: Accumulator[A => Err[B]], right: Accumulator[A]): Accumulator[B] = (left, right) match {
      case (l: Ap[xT, A => Err[B]], r) => {
        val x: Accumulator[xT => Err[A => Err[B]]] = l.left
        val y: Accumulator[xT] = l.right

        val newLeft = x.map[((xT, A)) => Err[B]] { fn => { case (x, a) => fn(x).andThen(_(a)) }}
        val newRight: Accumulator[(xT, A)] = ap(y.map { m: xT => a: A => Validated.valid((m, a)) }, r)

        Ap[(xT, A), B](newLeft, newRight)
      }
      case (OrElse(l0, l1), r) => OrElse(ap(l0, r), ap(l1, r))
      case (l, r) => Ap(l, r)
    }

    case class Ap[X, A](left: Accumulator[X => Err[A]], right: Accumulator[X]) extends Accumulator[A] {

      override def parseOption(name: Opts.Name) = {
        (left.parseOption(name), right.parseOption(name)) match {
          case (Some(leftMatch), None) => Some(leftMatch.map { ap(_, right) })
          case (None, Some(rightMatch)) => Some(rightMatch.map { ap(left, _) })
          case (None, None) => None
          case _ => Some(MatchAmbiguous)
        }
      }

      override def parseArg(arg: String) =
        left.parseArg(arg) match {
          case Left(newLeft) => right.parseArg(arg) match {
            case Left(newRight) => Left(ap(newLeft, newRight))
            case Right(newRight) => Right(ap(newLeft, newRight))
          }
          case Right(newLeft) => Right(ap(newLeft, right))
        }

      override def parseSub(command: String) = {
        val leftSub =
          left.parseSub(command)
            .map { parser =>
              parser andThen { _.map { leftResult =>
                (leftResult, right.result).mapN { _ apply _ }.mapValidated(identity)
              }}
            }

        val rightSub =
          right.parseSub(command)
            .map { parser =>
              parser andThen { _.map { rightResult =>
                (left.result, rightResult).mapN { _ apply _ }.mapValidated(identity)
              }}
            }

        leftSub <+> rightSub
      }

      override def result = (left.result ap right.result).mapValidated(identity)

      override def mapValidated[B](fn: (A) => Err[B]): Accumulator[B] = ap(left.map(_ andThen { _ andThen fn }), right)
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
        (left.parseArg(arg), right.parseArg(arg)) match {
          case (Left(newLeft), Left(newRight)) => Left(OrElse(newLeft, newRight))
          case (Left(newLeft), Right(newRight)) => Right(newRight)
          case (Right(newLeft), Left(newRight)) => Right(newLeft)
          case (Right(newLeft), Right(newRight)) => Right(OrElse(newLeft, newRight))

        }

      override def parseSub(command: String) = (left.parseSub(command), right.parseSub(command)) match {
        case (None, None) => None
        case (l, None) => l
        case (None, r) => r
        case (Some(l), Some(r)) => Some { args =>
          (l(args), r(args)) match {
            case (lh @ Left(_), _) => lh
            case (_, rh @ Left(_)) => rh
            case (Right(lv), Right(rv)) => Right(lv <+> rv)
          }
        }
      }

      override def result = left.result <+> right.result

      override def mapValidated[B](fn: (A) => Err[B]): Accumulator[B] = OrElse(left.mapValidated(fn), right.mapValidated(fn))
    }

    case class Regular(names: List[Opts.Name], visibility: Visibility, values: List[String] = Nil) extends Accumulator[NonEmptyList[String]] {

      override def parseOption(name: Opts.Name) =
        if (names contains name) Some(MatchOption { value => copy(values = value :: values) })
        else None

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

      override def parseSub(command: String) = None

      def result =
        NonEmptyList.fromList(List.fill(values)(()))
          .map(Result.success)
          .getOrElse(visibility match {
            case Visibility.Normal => Result.missingFlag(names.head)
            case _ => Result.fail
          })
    }

    case object Argument extends Accumulator[String] {

      override def parseArg(arg: String) = Right(Pure(Result.success(arg)))

      override def parseOption(name: Name) = None

      override def parseSub(command: String) = None

      override def result = Result.missingArgument
    }

    case class Arguments(stack: List[String]) extends Accumulator[NonEmptyList[String]] {

      override def parseArg(arg: String) = {
        val noMore = Pure(Result(Valid { () => Valid(NonEmptyList(arg, stack).reverse) }))
        val yesMore = Arguments(arg :: stack)
        Right(OrElse(noMore, yesMore))
      }

      override def parseOption(name: Name) = None

      override def parseSub(command: String) = None

      override def result: Result[NonEmptyList[String]] =
        NonEmptyList.fromList(stack.reverse)
          .map(Result.success)
          .getOrElse(Result.missingArgument)
    }

    case class Subcommand[A](name: String, action: Parser[A]) extends Accumulator[A] {

      override def parseOption(name: Name) = None

      override def parseSub(command: String) =
        if (command == name) Some(action andThen { _ map Result.success }) else None

      override def result = Result.missingCommand(name)
    }

    case class Validate[A, B](a: Accumulator[A], f: A => Validated[List[String], B]) extends Accumulator[B] {

      override def parseOption(name: Opts.Name) =
        a.parseOption(name).map { _.map { copy(_, f) } }

      override def parseArg(arg: String) =
        a.parseArg(arg) match {
          case Left(newA) => Left(newA.mapValidated(f))
          case Right(newA) => Right(newA.mapValidated(f))
        }

      override def parseSub(command: String) =
        a.parseSub(command).map { _ andThen { _.map { _.mapValidated(f) } } }

      override def result = a.result.mapValidated(f)

      override def mapValidated[C](fn: B => Err[C]) = Validate(a, f andThen { _ andThen fn })
    }

    def repeated[A](opt: Opt[A]): Accumulator[NonEmptyList[A]] = opt match {
      case Opt.Regular(name, _, _, visibility) => Regular(name, visibility)
      case Opt.Flag(name, _, visibility) => Flag(name, visibility)
      case Opt.Argument(_) => Arguments(Nil)
    }

    def fromOpts[A](opts: Opts[A]): Accumulator[A] = opts match {
      case Opts.Pure(a) => Accumulator.Pure(Result.success(a))
      case Opts.Missing => Accumulator.Pure(Result.fail)
      case Opts.HelpFlag(a) => fromOpts(a).mapValidated { _ => Validated.invalid(Nil) }
      case Opts.App(f, a) => Accumulator.ap(fromOpts(f).map(_ andThen Validated.valid), fromOpts(a))
      case Opts.OrElse(a, b) => OrElse(fromOpts(a), fromOpts(b))
      case Opts.Validate(a, validation) => fromOpts(a).mapValidated(validation andThen { _.leftMap(_.toList) })
      case Opts.Subcommand(command) => Subcommand(command.name, Parser(command))
      case Opts.Single(opt) => opt match {
        case Opt.Regular(name, _, _, visibility) => Regular(name, visibility).map { v: NonEmptyList[String] => v.toList.last }
        case Opt.Flag(name, _, visibility) => Flag(name, visibility).map{ v: NonEmptyList[Unit] => v.toList.last }
        case Opt.Argument(_) => Argument
      }
      case Opts.Repeated(opt) => repeated(opt)
    }
  }
}
