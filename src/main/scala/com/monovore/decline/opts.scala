package com.monovore.decline

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.{Applicative, MonoidK}
import cats.implicits._

/** A top-level argument parser, with all the info necessary to parse a full
  * set of arguments or display a useful help text.
  */
case class Command[+A](
  name: String = "program",
  header: String,
  options: Opts[A]
) {

  def showHelp: String = Help.fromCommand(this).toString

  def parse(args: Seq[String]): Either[Help, A] = Parser(this)(args.toList)
}

/** Represents zero or more command-line opts.
  */
sealed trait Opts[+A] {

  def mapValidated[B](fn: A => ValidatedNel[String, B]): Opts[B] = this match {
    case Opts.Validate(a, v) => Opts.Validate(a, v andThen { _ andThen (fn andThen Result.fromValidated) })
    case other => Opts.Validate(other, fn andThen Result.fromValidated)
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
    this.map { _ => true }.withDefault(false)

  override def toString: String = s"Opts(${Usage.fromOpts(this).flatMap { _.show }.mkString(" | ")})"
}

object Opts {

  sealed trait Name
  case class LongName(flag: String) extends Name { override val toString: String = s"--$flag"}
  case class ShortName(flag: Char) extends Name { override val toString: String = s"-$flag"}

  private[this] def namesFor(long: String, short: String): List[Name] = List(LongName(long)) ++ short.toList.map(ShortName)

  case class Pure[A](a: Result[A]) extends Opts[A]
  case class App[A, B](f: Opts[A => B], a: Opts[A]) extends Opts[B]
  case class OrElse[A](a: Opts[A], b: Opts[A]) extends Opts[A]
  case class Single[A](opt: Opt[A]) extends Opts[A]
  case class Repeated[A](opt: Opt[A]) extends Opts[NonEmptyList[A]]
  case class Subcommand[A](command: Command[A]) extends Opts[A]
  case class Validate[A, B](value: Opts[A], validate: A => Result[B]) extends Opts[B]

  implicit val alternative: Applicative[Opts] with MonoidK[Opts] =
    new Applicative[Opts] with MonoidK[Opts] {
      override def pure[A](x: A): Opts[A] = Opts.Pure(Result.success(x))
      override def ap[A, B](ff: Opts[A => B])(fa: Opts[A]): Opts[B] = Opts.App(ff, fa)
      override def empty[A]: Opts[A] = Opts.never
      override def combineK[A](x: Opts[A], y: Opts[A]): Opts[A] = Opts.OrElse(x, y)
    }

  private[this] def metavarFor[A](provided: String)(implicit arg: Argument[A]) =
    if (provided.isEmpty) arg.defaultMetavar else provided

  def apply[A](value: => A): Opts[A] = Pure(Result.success(())).map { _ => value }

  val never: Opts[Nothing] = Opts.Pure(Result.missing)

  def option[A : Argument](
    long: String,
    help: String,
    short: String = "",
    metavar: String = "",
    visibility: Visibility = Visibility.Normal
  ): Opts[A] =
    Single(Opt.Regular(namesFor(long, short), metavarFor[A](metavar), help, visibility))
      .mapValidated(Argument[A].read)

  def options[A : Argument](
    long: String,
    help: String,
    short: String = "",
    metavar: String = "",
    visibility: Visibility = Visibility.Normal
  ): Opts[NonEmptyList[A]] =
    Repeated(Opt.Regular(namesFor(long, short), metavarFor[A](metavar), help, visibility))
      .mapValidated { args => args.traverse[ValidatedNel[String, ?], A](Argument[A].read) }

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

  def argument[A : Argument](metavar: String = ""): Opts[A] =
    Single(Opt.Argument(metavarFor[A](metavar)))
      .mapValidated(Argument[A].read)

  def arguments[A : Argument](metavar: String = ""): Opts[NonEmptyList[A]] =
    Repeated(Opt.Argument(metavarFor[A](metavar)))
      .mapValidated { args => args.traverse[ValidatedNel[String, ?], A](Argument[A].read) }

  val help: Opts[Nothing] = {
    val helpFlag = flag("help", help = "Display this help text.", visibility = Visibility.Partial)
    Validate(helpFlag, { _: Unit => Result.failure() })
  }

  def subcommand[A](command: Command[A]): Opts[A] = Subcommand(command)

  def subcommand[A](name: String, help: String, helpFlag: Boolean = true)(opts: Opts[A]): Opts[A] = {
    val maybeHelp = if (helpFlag) Opts.help else Opts.never
    Subcommand(Command(name, help, maybeHelp orElse opts))
  }
}

sealed trait Opt[A]

object Opt {

  import Opts.Name

  case class Regular(names: List[Name], metavar: String, help: String, visibility: Visibility) extends Opt[String]
  case class Flag(names: List[Name], help: String, visibility: Visibility) extends Opt[Unit]
  case class Argument(metavar: String) extends Opt[String]
}