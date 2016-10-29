package com.monovore.decline

import cats.Applicative
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

  def withHelp: Command[A] = copy(
    options = (options |@| Opts.help).map { (real, _) => real }
  )

  def showHelp: String = Help.render(this)
}

/** A parser for zero or more command-line options.
  */
sealed trait Opts[A] {

  def mapValidated[B](fn: A => Result[B]): Opts[B] = this match {
    case Opts.Validate(a, v) => Opts.Validate(a, v andThen { _ andThen fn })
    case other => Opts.Validate(other, fn)
  }

  def map[B](fn: A => B) =
    mapValidated(fn andThen success)

  def withDefault[A0](default: => A0)(implicit isOption: A <:< Option[A0]) =
    map { _.getOrElse(default) }

  def validate(message: String)(fn: A => Boolean) = mapValidated { a =>
    if (fn(a)) success(a) else failure(message)
  }

  def withHelp: Opts[A] =
    (this |@| Opts.help).map { (real, _) => real }

  def parse(args: Seq[String]): Result[A] = Parse.apply(args.toList, this)
}

object Opts {

  case class Pure[A](a: A) extends Opts[A]
  case class App[A, B](f: Opts[A => B], a: Opts[A]) extends Opts[B]
  case class Single[A, B](opt: Opt[A], help: String)(val read: A => Result[B]) extends Opts[B]
  case class Validate[A, B](value: Opts[A], validate: A => Result[B]) extends Opts[B]

  implicit val applicative: Applicative[Opts] =
    new Applicative[Opts] {
      override def pure[A](x: A): Opts[A] = Opts.Pure(x)
      override def ap[A, B](ff: Opts[A => B])(fa: Opts[A]): Opts[B] = Opts.App(ff, fa)
    }

  def required[A : Read](long: String, metavar: String, help: String): Opts[A] =
    Single(Opt.Regular(long, metavar), help) {
      case Nil => failure(s"Missing mandatory option: --$long")
      case first :: Nil => Read[A](first)
      case _ => failure(s"Too many values for option: --$long")
    }

  def optional[A : Read](long: String, metavar: String = "STRING", help: String): Opts[Option[A]] =
    Single(Opt.Regular(long, metavar), help) {
      case Nil => success(None)
      case first :: Nil => Read[A](first).map(Some(_))
      case _ => failure(s"Too many values for option: --$long")
    }

  def flag(long: String, help: String): Opts[Boolean] =
    Single(Opt.Flag(long), help) {
      case 0 => success(false)
      case _ => success(true)
    }

  def requiredArg[A : Read](metavar: String): Opts[A] =
    Single(Opt.Arguments(metavar), "Unused.") {
      case List(arg) => Read[A](arg)
      case Nil => failure(s"Missing positional argument: $metavar")
    }

  def optionalArg[A : Read](metavar: String): Opts[Option[A]] =
    Single(Opt.Arguments(metavar), "Unused.") {
      case List(arg) => Read[A](arg).map(Some(_))
      case Nil => success(None)
    }

  def remainingArgs[A : Read](metavar: String): Opts[List[A]] =
    Single(Opt.Arguments(metavar, Int.MaxValue), "Unused.") { list =>
      Applicative[Result].sequence(list.map(Read[A]))
    }

  val help =
    Single(Opt.Flag("help"), "Display this help text") {
      case 0 => success(())
      case _ => failure()
    }
}

sealed trait Opt[A]

object Opt {
  case class Regular(name: String, metavar: String) extends Opt[List[String]]
  case class Flag(name: String) extends Opt[Int]
  case class Arguments(metavar: String, limit: Int = 1) extends Opt[List[String]] {
    require(limit > 0, "Requested number of arguments should be positive.")
  }
}