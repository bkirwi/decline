package com.monovore.decline

import cats.Functor
import cats.data.Validated.{Invalid, Valid}
import cats.data.{Ior, NonEmptyList, Validated}
import cats.syntax.all._
import com.monovore.decline.Opts.Name
import com.monovore.decline.Parser.Accumulator.OrElse

import scala.annotation.tailrec

private[decline] sealed trait ParseMode
private[decline] object ParseMode {
  case object Strict extends ParseMode
  case object Lenient extends ParseMode
}

private[decline] case class Parser[+A](command: Command[A])
    extends ((List[String], Map[String, String], ParseMode) => Ior[Help, A]) {

  import Parser._

  def apply(args: List[String], env: Map[String, String]): Either[Help, A] =
    apply(args, env, ParseMode.Strict) match {
      case Ior.Left(h) => Left(h)
      case Ior.Right(a) => Right(a)
      case Ior.Both(h, _) => Left(h)
    }

  def apply(args: List[String], env: Map[String, String], mode: ParseMode): Ior[Help, A] =
    consumeAll(args, Accumulator.fromOpts(command.options, env), mode) match {
      case Left(subcommandHelp) => Ior.left(subcommandHelp)
      case Right(result) => evalResult(result, mode)
    }

  private[this] val help = Help.fromCommand(command)

  private[this] def evalResult[A](out: Result[A], mode: ParseMode): Ior[Help, A] = {
    val warnings = out.warnings.distinct

    out.get match {
      case Invalid(failed) =>
        val allErrors = failed.messages.distinct.toList ++ warnings
        Ior.left(help.withErrors(allErrors))

      case Valid(fn) =>
        fn() match {
          case Invalid(validationMessages) =>
            val allErrors = validationMessages.distinct ++ warnings
            Ior.left(help.withErrors(allErrors))

          case Valid(result) =>
            if (warnings.isEmpty) {
              Ior.right(result)
            } else {
              mode match {
                case ParseMode.Strict => Ior.left(help.withErrors(warnings))
                case ParseMode.Lenient => Ior.both(help.withWarnings(warnings), result)
              }
            }
        }
    }
  }

  private[this] def toOption[B](args: ArgOut[B]): Option[Accumulator[B]] =
    args.collect { case Right(a) => a }.reduceOption(OrElse(_, _))

  private[this] def withWarning[B](acc: Accumulator[B], warning: String): Accumulator[B] =
    acc <* Accumulator.Pure(Result.success(()).withWarning(warning))

  @tailrec
  private[this] def consumeAll(
      args: List[String],
      accumulator: Accumulator[A],
      mode: ParseMode
  ): ConsumeResult[A] =
    args match {
      case LongOptWithEquals(option, value) :: rest =>
        accumulator.parseOption(Opts.LongName(option)) match {
          case Some(MatchFlag(_)) =>
            ConsumeResult.parsed(
              Result.fail.withError(Messages.unexpectedValueForFlag(s"--$option"))
            )
          case Some(MatchAmbiguous) =>
            ConsumeResult.parsed(Result.fail.withError(Messages.ambiguousOptionFlag(s"--$option")))
          case Some(MatchOption(next)) => consumeAll(rest, next(value), mode)
          case Some(MatchOptArg(next)) => consumeAll(rest, next(Some(value)), mode)
          case None =>
            mode match {
              case ParseMode.Strict =>
                ConsumeResult.parsed(Result.fail.withError(Messages.unexpectedOption(s"--$option")))
              case ParseMode.Lenient =>
                consumeAll(
                  rest,
                  withWarning(accumulator, Messages.unexpectedOption(s"--$option")),
                  mode
                )
            }
        }

      case LongOpt(option) :: rest =>
        accumulator.parseOption(Opts.LongName(option)) match {
          case Some(MatchFlag(next)) => consumeAll(rest, next, mode)
          case Some(MatchAmbiguous) =>
            ConsumeResult.parsed(Result.fail.withError(Messages.ambiguousOptionFlag(s"--$option")))
          case Some(MatchOptArg(next)) => consumeAll(rest, next(None), mode)
          case Some(MatchOption(next)) =>
            rest match {
              case Nil =>
                ConsumeResult.parsed(
                  Result.fail.withError(Messages.missingValueForOption(s"--$option"))
                )
              case value :: rest0 => consumeAll(rest0, next(value), mode)
            }
          case None =>
            mode match {
              case ParseMode.Strict =>
                ConsumeResult.parsed(Result.fail.withError(Messages.unexpectedOption(s"--$option")))
              case ParseMode.Lenient =>
                consumeAll(
                  rest,
                  withWarning(accumulator, Messages.unexpectedOption(s"--$option")),
                  mode
                )
            }
        }

      case "--" :: rest => consumeArgs(rest, accumulator, mode)

      case ShortOpt(NonEmptyString(flag, tail)) :: rest =>
        consumeShort(flag, tail, accumulator, rest, mode) match {
          case Right((newRest, newAccumulator)) =>
            consumeAll(newRest, newAccumulator, mode)
          case Left(result) => ConsumeResult.parsed(result)
        }

      case arg :: rest =>
        accumulator.parseSub(arg, mode) match {
          case Some(parseSubcommand) =>
            parseSubcommand(rest) match {
              case Left(h) => ConsumeResult.subcommandHelp(h.withPrefix(List(command.name)))
              case Right(r) => ConsumeResult.parsed(r)
            }
          case None =>
            toOption(accumulator.parseArg(arg)) match {
              case Some(next) => consumeAll(rest, next, mode)
              case None =>
                mode match {
                  case ParseMode.Strict =>
                    ConsumeResult.parsed(Result.fail.withError(Messages.unexpectedArgument(arg)))
                  case ParseMode.Lenient =>
                    consumeAll(
                      rest,
                      withWarning(accumulator, Messages.unexpectedArgument(arg)),
                      mode
                    )
                }
            }
        }

      case Nil => ConsumeResult.parsed(accumulator.result)
    }

  @tailrec
  private[this] def consumeShort(
      char: Char,
      tail: String,
      accumulator: Accumulator[A],
      rest: List[String],
      mode: ParseMode
  ): Either[Result[A], (List[String], Accumulator[A])] =
    accumulator.parseOption(Opts.ShortName(char)) match {
      case Some(MatchAmbiguous) =>
        Left(Result.fail.withError(Messages.ambiguousOptionFlag(s"-$char")))

      case Some(MatchFlag(next)) =>
        tail match {
          case "" => Right((rest, next))
          case NonEmptyString(nextFlag, nextTail) =>
            consumeShort(nextFlag, nextTail, next, rest, mode)
        }

      case Some(MatchOptArg(next)) =>
        tail match {
          case "" => Right((rest, next(None)))
          case value => Right((rest, next(Some(value))))
        }

      case Some(MatchOption(next)) =>
        tail match {
          case "" =>
            rest match {
              case Nil =>
                Left(Result.fail.withError(Messages.missingValueForOption(s"-$char")))
              case value :: rest0 => Right((rest0, next(value)))
            }
          case _ => Right((rest, next(tail)))
        }

      case None =>
        mode match {
          case ParseMode.Strict =>
            Left(Result.fail.withError(Messages.unexpectedOption(s"-$char")))
          case ParseMode.Lenient =>
            tail match {
              case "" =>
                Right((rest, withWarning(accumulator, Messages.unexpectedOption(s"-$char"))))
              case NonEmptyString(nextFlag, nextTail) =>
                consumeShort(
                  nextFlag,
                  nextTail,
                  withWarning(accumulator, Messages.unexpectedOption(s"-$char")),
                  rest,
                  mode
                )
            }
        }
    }

  @tailrec
  private[this] def consumeArgs(
      args: List[String],
      accumulator: Accumulator[A],
      mode: ParseMode
  ): ConsumeResult[A] =
    args match {
      case Nil => ConsumeResult.parsed(accumulator.result)
      case arg :: rest =>
        toOption(accumulator.parseArg(arg)) match {
          case Some(next) => consumeArgs(rest, next, mode)
          case None =>
            mode match {
              case ParseMode.Strict =>
                ConsumeResult.parsed(Result.fail.withError(Messages.unexpectedArgument(arg)))
              case ParseMode.Lenient =>
                consumeArgs(rest, withWarning(accumulator, Messages.unexpectedArgument(arg)), mode)
            }
        }
    }
}

private[decline] object Parser {

  /**
   * Result of consuming arguments: either a subcommand's Help to return directly, or a Result to
   * evaluate
   */
  private type ConsumeResult[+A] = Either[Help, Result[A]]
  private object ConsumeResult {
    def subcommandHelp[A](help: Help): ConsumeResult[A] = Left(help)
    def parsed[A](result: Result[A]): ConsumeResult[A] = Right(result)
  }

  private[decline] sealed trait Match[+A]
  private[decline] case class MatchFlag[A](next: A) extends Match[A]
  private[decline] case class MatchOption[A](next: String => A) extends Match[A]
  private[decline] case class MatchOptArg[A](next: Option[String] => A) extends Match[A]
  private[decline] case object MatchAmbiguous extends Match[Nothing]

  private[decline] implicit val matchFunctor: Functor[Match] = new Functor[Match] {
    override def map[A, B](fa: Match[A])(f: A => B): Match[B] = fa match {
      case MatchFlag(next) => MatchFlag(f(next))
      case MatchOption(next) => MatchOption(next andThen f)
      case MatchOptArg(next) => MatchOptArg(next andThen f)
      case MatchAmbiguous => MatchAmbiguous
    }
  }

  private[decline] type ArgOut[+A] = NonEmptyList[Either[Accumulator[A], Accumulator[A]]]

  private[decline] def squish[A](argOut: ArgOut[A]): ArgOut[A] = {
    @tailrec
    def merge(
        current: Either[Accumulator[A], Accumulator[A]],
        rest: List[Either[Accumulator[A], Accumulator[A]]],
        acc: List[Either[Accumulator[A], Accumulator[A]]]
    ): NonEmptyList[Either[Accumulator[A], Accumulator[A]]] =
      rest match {
        case Nil =>
          acc.reverse match {
            case Nil => NonEmptyList.one(current)
            case head :: tail => NonEmptyList(head, tail :+ current)
          }
        case next :: tail =>
          (current, next) match {
            case (Left(x), Left(y)) => merge(Left(OrElse(x, y)), tail, acc)
            case (Right(x), Right(y)) => merge(Right(OrElse(x, y)), tail, acc)
            case _ => merge(next, tail, current :: acc)
          }
      }
    merge(argOut.head, argOut.tail, Nil)
  }

  private[decline] type Err[+A] = Validated[List[String], A]

  sealed trait Accumulator[+A] {
    def parseOption(name: Opts.Name): Option[Match[Accumulator[A]]]
    def parseArg(arg: String): ArgOut[A] = NonEmptyList.of(Left(this))
    def parseSub(command: String, mode: ParseMode): Option[List[String] => ConsumeResult[A]]
    def result: Result[A]

    def mapValidated[B](fn: A => Err[B]): Accumulator[B] =
      Accumulator.Validate(this, fn)

    final def map[B](fn: A => B): Accumulator[B] = mapValidated(fn andThen Validated.valid)

    /** Sequences this accumulator with another, keeping only the left (this) value */
    final def <*[B](that: Accumulator[B]): Accumulator[A] =
      Accumulator.Ap(this.map(a => (_: B) => a), that)
  }

  val LongOpt = "--(.+)".r
  val LongOptWithEquals = "--(.+?)=(.+)".r
  val ShortOpt = "-(.+)".r

  object NonEmptyString {
    def unapply(string: String): Option[(Char, String)] =
      if (string.isEmpty) None
      else Some(string.charAt(0) -> string.substring(1))
  }

  object Accumulator {

    case class Pure[A](value: Result[A]) extends Accumulator[A] {

      override def parseOption(name: Name) = None

      override def parseSub(command: String, mode: ParseMode) = None

      override def result = value

      override def mapValidated[B](fn: A => Err[B]): Accumulator[B] = Pure(value.mapValidated(fn))
    }

    def ap[A, B](left: Accumulator[A => B], right: Accumulator[A]): Accumulator[B] =
      (left, right) match {
        case (l, r) => Ap(l, r)
      }

    case class Ap[X, A](left: Accumulator[X => A], right: Accumulator[X]) extends Accumulator[A] {

      override def parseOption(name: Opts.Name) = {
        (left.parseOption(name), right.parseOption(name)) match {
          case (Some(leftMatch), None) => Some(leftMatch.map { ap(_, right) })
          case (None, Some(rightMatch)) => Some(rightMatch.map { ap(left, _) })
          case (None, None) => None
          case _ => Some(MatchAmbiguous)
        }
      }

      override def parseArg(arg: String) = {

        lazy val parsedRight = squish(right.parseArg(arg))

        squish(left.parseArg(arg))
          .flatMap {
            // Left side can't accept the argument: try the right
            case Left(newLeft) =>
              parsedRight.map {
                case Left(newRight) => Left(ap(newLeft, newRight))
                case Right(newRight) => Right(ap(newLeft, newRight))
              }
            case Right(newLeft) => NonEmptyList.of(Right(ap(newLeft, right)))
          }
      }

      override def parseSub(command: String, mode: ParseMode) = {
        val leftSub =
          left
            .parseSub(command, mode)
            .map(parser =>
              parser andThen {
                _.map(leftResult => (leftResult, right.result).mapN(_ apply _))
              }
            )

        val rightSub =
          right
            .parseSub(command, mode)
            .map(parser =>
              parser andThen {
                _.map(rightResult => (left.result, rightResult).mapN(_ apply _))
              }
            )

        leftSub <+> rightSub
      }

      override def result = left.result ap right.result
    }

    case class OrElse[A](left: Accumulator[A], right: Accumulator[A]) extends Accumulator[A] {

      override def parseOption(name: Name) =
        (left.parseOption(name), right.parseOption(name)) match {
          case (Some(MatchFlag(l)), Some(MatchFlag(r))) =>
            Some(MatchFlag(OrElse(l, r)))
          case (Some(MatchOption(l)), Some(MatchOption(r))) =>
            Some(MatchOption(value => OrElse(l(value), r(value))))
          case (Some(_), Some(_)) => Some(MatchAmbiguous)
          case (l @ Some(_), None) => l
          case (None, r @ Some(_)) => r
          case (None, None) => None
        }

      override def parseArg(arg: String) =
        left.parseArg(arg) concatNel right.parseArg(arg)

      override def parseSub(command: String, mode: ParseMode) =
        (left.parseSub(command, mode), right.parseSub(command, mode)) match {
          case (None, None) => None
          case (l, None) => l
          case (None, r) => r
          case (Some(l), Some(r)) =>
            Some(args =>
              (l(args), r(args)) match {
                case (lh @ Left(_), _) => lh
                case (_, rh @ Left(_)) => rh
                case (Right(lv), Right(rv)) => Right(lv <+> rv)
              }
            )
        }

      override def result = left.result <+> right.result

      override def mapValidated[B](fn: A => Err[B]): Accumulator[B] =
        OrElse(left.mapValidated(fn), right.mapValidated(fn))
    }

    case class Regular(names: List[Opts.Name], visibility: Visibility, values: List[String] = Nil)
        extends Accumulator[NonEmptyList[String]] {

      override def parseOption(name: Opts.Name) =
        if (names contains name) Some(MatchOption(value => copy(values = value :: values)))
        else None

      override def parseSub(command: String, mode: ParseMode) = None

      def result =
        NonEmptyList
          .fromList(values.reverse)
          .map(Result.success)
          .getOrElse(visibility match {
            case Visibility.Normal => Result.missingFlag(names.head)
            case _ => Result.fail
          })
    }

    case class OptionalOptArg(
        names: List[Opts.Name],
        visibility: Visibility,
        reversedValues: List[Option[String]] = Nil
    ) extends Accumulator[NonEmptyList[Option[String]]] {

      override def parseOption(name: Opts.Name) =
        if (names contains name)
          Some(MatchOptArg(arg => copy(reversedValues = arg :: reversedValues)))
        else None

      override def parseSub(command: String, mode: ParseMode) = None

      override def result =
        NonEmptyList
          .fromList(reversedValues.reverse)
          .map(Result.success)
          .getOrElse(visibility match {
            case Visibility.Normal => Result.missingFlag(names.head)
            case _ => Result.fail
          })
    }

    case class Flag(names: List[Opts.Name], visibility: Visibility, values: Int = 0)
        extends Accumulator[NonEmptyList[Unit]] {

      override def parseOption(name: Opts.Name) =
        if (names contains name) Some(MatchFlag(copy(values = values + 1)))
        else None

      override def parseSub(command: String, mode: ParseMode) = None

      def result =
        NonEmptyList
          .fromList(List.fill(values)(()))
          .map(Result.success)
          .getOrElse(visibility match {
            case Visibility.Normal => Result.missingFlag(names.head)
            case _ => Result.fail
          })
    }

    case object Argument extends Accumulator[String] {

      override def parseArg(arg: String) = NonEmptyList.of(Right(Pure(Result.success(arg))))

      override def parseOption(name: Name) = None

      override def parseSub(command: String, mode: ParseMode) = None

      override def result = Result.missingArgument
    }

    case class Arguments(stack: List[String]) extends Accumulator[NonEmptyList[String]] {

      override def parseArg(arg: String) = {
        val noMore = Pure(Result(Valid(() => Valid(NonEmptyList(arg, stack).reverse))))
        val yesMore = Arguments(arg :: stack)
        NonEmptyList.of(Right(OrElse(noMore, yesMore)))
      }

      override def parseOption(name: Name) = None

      override def parseSub(command: String, mode: ParseMode) = None

      override def result: Result[NonEmptyList[String]] =
        NonEmptyList
          .fromList(stack.reverse)
          .map(Result.success)
          .getOrElse(Result.missingArgument)
    }

    case class Subcommand[A](name: String, action: Parser[A], env: Map[String, String])
        extends Accumulator[A] {

      override def parseOption(name: Name) = None

      override def parseSub(command: String, mode: ParseMode) = {
        if (command == name) Some { (args: List[String]) =>
          action(args, env, mode) match {
            case Ior.Left(h) => Left(h)
            case Ior.Right(a) => Right(Result.success(a))
            case Ior.Both(h, a) => Right(Result.success(a).copy(warnings = h.warnings))
          }
        }
        else None
      }

      override def result = Result.missingCommand(name)
    }

    case class Validate[A, B](a: Accumulator[A], f: A => Validated[List[String], B])
        extends Accumulator[B] {

      override def parseOption(name: Opts.Name) =
        a.parseOption(name).map { _.map { copy(_, f) } }

      override def parseArg(arg: String) =
        a.parseArg(arg).map {
          case Left(newA) => Left(newA.mapValidated(f))
          case Right(newA) => Right(newA.mapValidated(f))
        }

      override def parseSub(command: String, mode: ParseMode) =
        a.parseSub(command, mode).map { _ andThen { _.map { _.mapValidated(f) } } }

      override def result = a.result.mapValidated(f)

      override def mapValidated[C](fn: B => Err[C]) = Validate(a, f andThen { _ andThen fn })
    }

    def repeated[A](opt: Opt[A]): Accumulator[NonEmptyList[A]] = opt match {
      case Opt.Regular(name, _, _, visibility) => Regular(name, visibility)
      case Opt.Flag(name, _, visibility) => Flag(name, visibility)
      case Opt.Argument(_) => Arguments(Nil)
      case Opt.OptionalOptArg(name, _, _, visibility) => OptionalOptArg(name, visibility)
    }

    def fromOpts[A](opts: Opts[A], env: Map[String, String]): Accumulator[A] = opts match {
      case Opts.Pure(a) => Accumulator.Pure(Result.success(a))
      case Opts.Missing => Accumulator.Pure(Result.fail)
      case Opts.HelpFlag(a) =>
        fromOpts(a, env).mapValidated(_ => Validated.invalid(Nil))

      case Opts.App(f, a) => Accumulator.ap(fromOpts(f, env), fromOpts(a, env))
      case Opts.OrElse(a, b) => OrElse(fromOpts(a, env), fromOpts(b, env))
      case Opts.Validate(a, validation) =>
        fromOpts(a, env).mapValidated(validation andThen { _.leftMap(_.toList) })
      case Opts.Subcommand(command) => Subcommand(command.name, Parser(command), env)
      case Opts.Single(opt) =>
        opt match {
          case Opt.OptionalOptArg(name, _, _, visibility) =>
            OptionalOptArg(name, visibility).map(_.toList.last)
          case Opt.Regular(name, _, _, visibility) =>
            Regular(name, visibility).map(_.toList.last)
          case Opt.Flag(name, _, visibility) =>
            Flag(name, visibility).map(_.toList.last)
          case Opt.Argument(_) => Argument
        }
      case Opts.Repeated(opt) => repeated(opt)
      case Opts.Env(name, _, _) =>
        Accumulator.Pure(
          env
            .get(name)
            .map(Result.success)
            .getOrElse(Result.missingEnvVar(name))
        )
    }
  }
}
