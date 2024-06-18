package com.monovore.decline

import cats.Alternative
import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import cats.syntax.all._
import com.monovore.decline.Parser._
import com.monovore.decline.ParserConfig._

import scala.util.matching.Regex

trait ParserConfig[T] extends Consumer[T] {
  val consumer: Consumer[T]

  val help: Help = Help(Nil, NonEmptyList("", Nil), Nil, Nil)

  def apply(x: Consumable[T]): Consumed[T] = consumer(x)
  def isDefinedAt(x: Consumable[T]): Boolean = consumer.isDefinedAt(x)

  private[this] def error(reason: String*): Either[Help, T] =
    Either.left(help.withErrors(reason.toList))

  private[decline] def toOption(args: ArgOut[T]): Option[Accumulator[T]] =
    args.collect { case Right(a) => a }.reduceOption(Accumulator.OrElse(_, _))

  private[decline] def evalResult(out: Result[T]): Either[Help, T] = out.get match {
    case Invalid(failed) => error(failed.messages.distinct: _*)
    // NB: if any of the user-provided functions have side-effects, they will happen here!
    case Valid(fn) =>
      fn() match {
        case Invalid(messages) => error(messages.distinct: _*)
        case Valid(result) => Either.right(result)
      }
  }

  def fail(reason: String*): Consumed[T] = Either.right(error(reason: _*)) :: Nil
  def done(value: Either[Help, T]): Consumed[T] = Either.right(value) :: Nil
  def continue(value: Consumable[T]): Consumed[T] = Either.left(value) :: Nil
  def doneWithResult(value: Result[T]): Consumed[T] = done(evalResult(value))
}

case class LongOptWithEqualsParser[T](
    skipUnknown: Boolean = false,
    pattern: Regex = "--(.+?)=(.+)".r
) extends ParserConfig[T] {
  override val consumer: Consumer[T] = { case Consumable(pattern(option, value) :: rest, acc) =>
    acc.parseOption(Opts.LongName(option)) match {
      case Some(MatchFlag(next)) =>
        fail(s"Got unexpected value for flag: --$option")
      case Some(MatchAmbiguous) =>
        fail(s"Ambiguous option/flag: --$option")
      case Some(MatchOption(next)) =>
        continue(Consumable(rest, next(value)))
      case Some(MatchOptArg(next)) =>
        continue(Consumable(rest, next(Some(value))))
      case None =>
        if (skipUnknown) continue(Consumable(rest, acc))
        else fail(s"Unexpected option: --$option")
    }
  }
}

case class LongOptParser[T](skipUnknown: Boolean = false, pattern: Regex = "--(.+)".r)
    extends ParserConfig[T] {

  override val consumer: Consumer[T] = { case Consumable(pattern(option) :: rest, acc) =>
    acc.parseOption(Opts.LongName(option)) match {
      case Some(MatchFlag(next)) => continue(Consumable(rest, next))
      case Some(MatchAmbiguous) => fail(s"Ambiguous option/flag: --$option")
      case Some(MatchOptArg(next)) => continue(Consumable(rest, next(None)))
      case Some(MatchOption(next)) =>
        rest match {
          case Nil => fail(s"Missing value for option: --$option")
          case value :: rest0 => continue(Consumable(rest0, next(value)))
        }
      case None =>
        if (skipUnknown) continue(Consumable(rest, acc))
        else fail(s"Unexpected option: --$option")
    }
  }
}

case class ShorOptParser[T](skipUnknown: Boolean = false, pattern: Regex = "-(.+)".r)
    extends ParserConfig[T] {
  override val consumer: Consumer[T] = {
    case Consumable(pattern(NonEmptyString(flag, tail)) :: rest, acc) =>
      acc.parseOption(Opts.ShortName(flag)) match {
        case Some(MatchAmbiguous) =>
          fail(s"Ambiguous option/flag: -$flag")
        case Some(MatchFlag(next)) =>
          tail match {
            case "" =>
              continue(Consumable(rest, next))
            case NonEmptyString(nextFlag, nextTail) =>
              continue(
                Consumable(s"-$nextFlag$nextTail" +: rest, next)
              )
          }
        case Some(MatchOptArg(next)) =>
          tail match {
            case "" => continue(Consumable(rest, next(None)))
            case value => continue(Consumable(rest, next(Some(value))))
          }
        case Some(MatchOption(next)) =>
          tail match {
            case "" =>
              rest match {
                case Nil => fail(s"Missing value for option: -$flag")
                case value :: rest0 => continue(Consumable(rest0, next(value)))
              }
            case _ => continue(Consumable(rest, next(tail)))
          }
        case None =>
          if (skipUnknown) continue(Consumable(rest, acc)) else fail(s"Unexpected option: -$flag")
      }
  }
}

case class ResultParser[T]() extends ParserConfig[T] {
  override val consumer: Consumer[T] = { case Consumable(Nil, acc) => doneWithResult(acc.result) }
}

case class ArgsConsumer[T](skipUnknown: Boolean = false) extends ParserConfig[T] {
  override val consumer: Consumer[T] = { case Consumable(arg :: rest, acc) =>
    toOption(acc.parseArg(arg)) match {
      case Some(next) => continue(Consumable(rest, next))
      case None =>
        if (skipUnknown) continue(Consumable(rest, acc)) else fail(s"Unexpected argument: $arg")
    }
  }
}

case class ArgsParser[T](skipUnknown: Boolean = false, divider: String = "--")
    extends ParserConfig[T] {
  override val consumer: Consumer[T] = {
    case Consumable(head :: rest, acc) if head == divider =>
      continue(Consumable(rest, acc))
      done(
        Consumable(rest, acc).tailRecM(ResultParser[T]() orElse ArgsConsumer[T](skipUnknown)).last
      )
  }
}

case class SubcommandParser[T](skipUnknown: Boolean = false, commandName: String)
    extends ParserConfig[T] {
  override val consumer: Consumer[T] = { case cons @ Consumable(arg :: rest, acc) =>
    acc
      .parseSub(arg)
      .map(result => result(rest).leftMap(_.withPrefix(commandName :: Nil)).flatMap(evalResult))
      .fold(ArgsConsumer[T](skipUnknown).apply(cons))(done)
  }
}

case class BuildParser[T](command: Command[T], config: ParserConfiguration[T])
    extends ParserConfig[T] {
  override val help = Help.fromCommand(command)

  val defaultParser = LongOptWithEqualsParser[T]() ::
    LongOptParser[T]() ::
    ArgsParser[T]() ::
    ShorOptParser[T]() ::
    SubcommandParser[T](commandName = command.name) ::
    ResultParser[T]() :: Nil

  val parser =
    if (config.prepend) config.parserConfig ++ defaultParser
    else config.parserConfig :+ ResultParser[T]()
  override val consumer: Consumer[T] = parser.reduceLeft[Consumer[T]](_ orElse _)
}

case class ParserConfiguration[T](
    prepend: Boolean = true,
    parserConfig: List[ParserConfig[T]] = Nil
)

object ParserConfig {

  case class Consumable[A](args: List[String], accumulator: Accumulator[A])

  type Consumed[A] = List[Either[Consumable[A], Either[Help, A]]]
  type Consumer[T] = PartialFunction[Consumable[T], Consumed[T]]

  def fromCommand[T](
      command: Command[T]
  )(implicit parserConfiguration: ParserConfiguration[T]): ParserConfig[T] =
    BuildParser(command, parserConfiguration)
}
