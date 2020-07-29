package com.monovore.decline

import cats.{Alternative, Monoid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}

/** A top-level argument parser, with all the info necessary to parse a full
 * set of arguments or display a useful help text.
 */
class Command[+A] private[decline] (
    val name: String,
    val header: String,
    val options: Opts[A]
) {

  def showHelp: String = Help.fromCommand(this).toString

  def parse(args: Seq[String], env: Map[String, String] = Map.empty): Either[Help, A] =
    Parser(this)(args.toList, env)

  def mapValidated[B](function: A => ValidatedNel[String, B]): Command[B] =
    new Command(name, header, options.mapValidated(function))

  def map[B](fn: A => B): Command[B] =
    mapValidated(fn andThen Validated.valid)

  def validate(message: String)(fn: A => Boolean): Command[A] =
    mapValidated { a => if (fn(a)) Validated.valid(a) else Validated.invalidNel(message) }
}

object Command {
  def apply[A](name: String, header: String, helpFlag: Option[Opts[Nothing]] = Some(Opts.help))(
      opts: Opts[A]
  ): Command[A] = {
    val maybeHelp = helpFlag.getOrElse(Opts.never)
    new Command(name, header, maybeHelp orElse opts)
  }
}

/** Represents zero or more command-line opts.
 */
sealed trait Opts[+A] {

  def mapValidated[B](fn: A => ValidatedNel[String, B]): Opts[B] = this match {
    case Opts.Validate(a, v) => Opts.Validate(a, v andThen { _ andThen fn })
    case other => Opts.Validate(other, fn)
  }

  def map[B](fn: A => B) =
    mapValidated(fn andThen Validated.valid)

  def validate(message: String)(fn: A => Boolean) = mapValidated { a =>
    if (fn(a)) Validated.valid(a) else Validated.invalidNel(message)
  }

  def orElse[A0 >: A](other: Opts[A0]): Opts[A0] = Opts.OrElse(this, other)

  def withDefault[A0 >: A](default: A0): Opts[A0] =
    this orElse Opts.apply(default)

  def orNone: Opts[Option[A]] =
    this.map(Some(_)).withDefault(None)

  def orEmpty[A0](implicit nonEmpty: A <:< NonEmptyList[A0]): Opts[List[A0]] =
    this.map(_.toList).withDefault(Nil)

  def orFalse(implicit isUnit: A <:< Unit): Opts[Boolean] =
    this
      .map(_ => true)
      .withDefault(false)

  def orTrue(implicit isUnit: A <:< Unit): Opts[Boolean] =
    this
      .map(_ => false)
      .withDefault(true)

  def asHelp(implicit isUnit: A <:< Unit): Opts[Nothing] =
    Opts.HelpFlag(this.map(isUnit))

  override def toString: String =
    s"Opts(${Usage.fromOpts(this).flatMap { _.show }.mkString(" | ")})"
}

object Opts {

  sealed trait Name
  case class LongName(flag: String) extends Name { override val toString: String = s"--$flag" }
  case class ShortName(flag: Char) extends Name { override val toString: String = s"-$flag" }

  private[this] def namesFor(long: String, short: String): List[Name] =
    List(LongName(long)) ++ short.toList.map(ShortName)

  private[decline] case class Pure[A](a: A) extends Opts[A]
  private[decline] case object Missing extends Opts[Nothing]
  private[decline] case class App[A, B](f: Opts[A => B], a: Opts[A]) extends Opts[B]
  private[decline] case class OrElse[A](a: Opts[A], b: Opts[A]) extends Opts[A]
  private[decline] case class Single[A](opt: Opt[A]) extends Opts[A]
  private[decline] case class Repeated[A](opt: Opt[A]) extends Opts[NonEmptyList[A]]
  private[decline] case class Subcommand[A](command: Command[A]) extends Opts[A]
  private[decline] case class Validate[A, B](value: Opts[A], validate: A => ValidatedNel[String, B])
      extends Opts[B]
  private[decline] case class HelpFlag(flag: Opts[Unit]) extends Opts[Nothing]
  private[decline] case class Env(name: String, help: String, metavar: String) extends Opts[String]

  implicit val alternative: Alternative[Opts] =
    new Alternative[Opts] {
      override def unit: Opts[Unit] = Opts.unit
      override def pure[A](x: A): Opts[A] = Opts.Pure(x)
      override def ap[A, B](ff: Opts[A => B])(fa: Opts[A]): Opts[B] = Opts.App(ff, fa)
      override def empty[A]: Opts[A] = Opts.never
      override def combineK[A](x: Opts[A], y: Opts[A]): Opts[A] = Opts.OrElse(x, y)
    }

  implicit def monoid[A]: Monoid[Opts[A]] = alternative.algebra[A]

  private[this] def metavarFor[A](provided: String)(implicit arg: Argument[A]) =
    if (provided.isEmpty) arg.defaultMetavar else provided

  val unit: Opts[Unit] = Pure(())

  def apply[A](value: => A): Opts[A] = unit.map(_ => value)

  val never: Opts[Nothing] = Opts.Missing

  def option[A: Argument](
      long: String,
      help: String,
      short: String = "",
      metavar: String = "",
      visibility: Visibility = Visibility.Normal
  ): Opts[A] =
    Single(Opt.Regular(namesFor(long, short), metavarFor[A](metavar), help, visibility))
      .mapValidated(Argument[A].read)

  def options[A: Argument](
      long: String,
      help: String,
      short: String = "",
      metavar: String = "",
      visibility: Visibility = Visibility.Normal
  ): Opts[NonEmptyList[A]] =
    Repeated(Opt.Regular(namesFor(long, short), metavarFor[A](metavar), help, visibility))
      .mapValidated(args => args.traverse[ValidatedNel[String, ?], A](Argument[A].read))

  def flag(
      long: String,
      help: String,
      short: String = "",
      visibility: Visibility = Visibility.Normal
  ): Opts[Unit] =
    Single(Opt.Flag(namesFor(long, short), help, visibility))

  def flags(
      long: String,
      help: String,
      short: String = "",
      visibility: Visibility = Visibility.Normal
  ): Opts[Int] =
    Repeated(Opt.Flag(namesFor(long, short), help, visibility)).map { _.toList.size }

  def argument[A: Argument](metavar: String = ""): Opts[A] =
    Single(Opt.Argument(metavarFor[A](metavar)))
      .mapValidated(Argument[A].read)

  def arguments[A: Argument](metavar: String = ""): Opts[NonEmptyList[A]] =
    Repeated(Opt.Argument(metavarFor[A](metavar)))
      .mapValidated(args => args.traverse[ValidatedNel[String, ?], A](Argument[A].read))

  val help: Opts[Nothing] =
    flag("help", help = "Display this help text.", short = "h", visibility = Visibility.Partial).asHelp

  def subcommand[A](command: Command[A]): Opts[A] = Subcommand(command)

  def subcommands[A](head: Command[A], tail: Command[A]*): Opts[A] =
    NonEmptyList.of(head, tail: _*).map(subcommand(_)).reduce

  def subcommand[A](name: String, help: String, helpFlag: Option[Opts[Nothing]] = Some(Opts.help))(
      opts: Opts[A]
  ): Opts[A] = {
    Subcommand(Command(name, help, helpFlag)(opts))
  }

  def env[A: Argument](name: String, help: String, metavar: String = ""): Opts[A] =
    Env(name, help, metavarFor[A](metavar)).mapValidated(Argument[A].read)
}

private[decline] sealed trait Opt[A]

private[decline] object Opt {

  import Opts.Name

  case class Regular(names: List[Name], metavar: String, help: String, visibility: Visibility)
      extends Opt[String]
  case class Flag(names: List[Name], help: String, visibility: Visibility) extends Opt[Unit]
  case class Argument(metavar: String) extends Opt[String]
}
