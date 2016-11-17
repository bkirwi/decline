package com.monovore.decline

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import cats.{Alternative, Applicative}
import cats.implicits._
import com.monovore.decline.Result._

/** A top-level argument parser, with all the info necessary to parse a full
  * set of arguments or display a useful help text.
  */
case class Command[A](
  name: String = "program",
  header: String,
  options: Opts[A]
) {

  def showHelp: String = Help.render(this)
}

/** A parser for zero or more command-line options.
  */
sealed trait Opts[+A] {

  def mapValidated[B](fn: A => Result[B]): Opts[B] = this match {
    case Opts.Validate(a, v) => Opts.Validate(a, v andThen { _ andThen fn })
    case other => Opts.Validate(other, fn)
  }

  def map[B](fn: A => B) =
    mapValidated(fn andThen success)

  def validate(message: String)(fn: A => Boolean) = mapValidated { a =>
    if (fn(a)) success(a) else failure(message)
  }

  def orElse[A0 >: A](other: Opts[A0]): Opts[A0] = Opts.OrElse(this, other)

  def withDefault[A0 >: A](default: A0): Opts[A0] =
    this orElse Opts.value(default)

  def orNone: Opts[Option[A]] =
    this.map(Some(_)).withDefault(None)

  def orEmpty[A0](implicit nonEmpty: A <:< NonEmptyList[A0]): Opts[List[A0]] =
    this.map(_.toList).withDefault(Nil)

  def orFalse(implicit isUnit: A <:< Unit): Opts[Boolean] =
    this.map { _ => true }.withDefault(false)

  def parse(args: Seq[String]): Result[A] = Parse.apply(args.toList, this)
}

object Opts {

  sealed trait Name
  case class LongName(flag: String) extends Name { override val toString: String = s"--$flag"}
  case class ShortName(flag: Char) extends Name { override val toString: String = s"-$flag"}

  private[this] def namesFor(long: String, short: String): List[Name] = List(LongName(long)) ++ short.toList.map(ShortName(_))

  case class Pure[A](a: Result[A]) extends Opts[A]
  case class App[A, B](f: Opts[A => B], a: Opts[A]) extends Opts[B]
  case class OrElse[A](a: Opts[A], b: Opts[A]) extends Opts[A]
  case class Single[A](opt: Opt[A]) extends Opts[A]
  case class Repeated[A](opt: Opt[A]) extends Opts[NonEmptyList[A]]
  case class Subcommand[A](command: Command[A]) extends Opts[A]
  case class Validate[A, B](value: Opts[A], validate: A => Result[B]) extends Opts[B]

  implicit val alternative: Alternative[Opts] =
    new Alternative[Opts] {
      override def pure[A](x: A): Opts[A] = Opts.Pure(Result.success(x))
      override def ap[A, B](ff: Opts[A => B])(fa: Opts[A]): Opts[B] = Opts.App(ff, fa)
      override def empty[A]: Opts[A] = Opts.Pure(Result.failure[A]())
      override def combineK[A](x: Opts[A], y: Opts[A]): Opts[A] = Opts.OrElse(x, y)
    }

  private[this] def metavarFor[A](provided: String)(implicit arg: Argument[A]) =
    if (provided.isEmpty) arg.defaultMetavar else provided

  def value[A](value: A): Opts[A] = Pure(Result.success(value))

  val never: Opts[Nothing] = Opts.Pure(Invalid(Nil))

  def option[A : Argument](long: String, help: String, short: String = "", metavar: String = ""): Opts[A] =
    Single(Opt.Regular(namesFor(long, short), metavarFor[A](metavar), help))
      .mapValidated(Argument[A].read)

  def options[A : Argument](long: String, help: String, short: String = "", metavar: String = ""): Opts[NonEmptyList[A]] =
    Repeated(Opt.Regular(namesFor(long, short), metavarFor[A](metavar), help))
      .mapValidated { args => args.traverse(Argument[A].read) }

  def flag(long: String, help: String, short: String = ""): Opts[Unit] =
    Single(Opt.Flag(namesFor(long, short), help))

  def argument[A : Argument](metavar: String = ""): Opts[A] =
    Single(Opt.Argument(metavarFor[A](metavar)))
      .mapValidated(Argument[A].read)

  def arguments[A : Argument](metavar: String = ""): Opts[NonEmptyList[A]] =
    Repeated(Opt.Argument(metavarFor[A](metavar)))
      .mapValidated { args => args.traverse(Argument[A].read) }

  val help =
    flag("help", help = "Display this help text.")
      .mapValidated { _ => Result.failure() }

  def subcommand[A](name: String, help: String)(opts: Opts[A]): Opts[A] = Subcommand(Command(name, help, opts))
}

sealed trait Opt[A]

object Opt {

  import Opts.Name

  case class Regular(names: List[Name], metavar: String, help: String) extends Opt[String]
  case class Flag(names: List[Name], help: String) extends Opt[Unit]
  case class Argument(metavar: String) extends Opt[String]
}