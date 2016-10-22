package com.monovore.clique

import cats.Applicative
import cats.free.FreeApplicative

import scala.util.{Failure, Success, Try}

/** A top-level argument parser, with all the info necessary to parse a full
  * set of arguments or display a useful help text.
  */
case class Command[A](
  name: String = "program",
  header: String,
  options: Opts[A]
)

/** A parser for zero or more command-line options.
  */
case class Opts[A](value: FreeApplicative[Opt, A]) {
  def map[B](fn: A => B) = Opts(value.map(fn))
  def withDefault[A0](default: => A0)(implicit isOption: A <:< Option[A0]) =
    map { _.getOrElse(default) }
}

object Opts {

  type F[A] = FreeApplicative[Opt, A]

  def apply[A](opt: Opt[A]): Opts[A] = Opts(FreeApplicative.lift(opt))

  // TODO: kittens!
  implicit val applicative: Applicative[Opts] = {
    val free = FreeApplicative.freeApplicative[Opt]
    new Applicative[Opts] {
      override def pure[A](x: A): Opts[A] = Opts(free.pure(x))
      override def ap[A, B](ff: Opts[A => B])(fa: Opts[A]): Opts[B] = Opts(free.ap(ff.value)(fa.value))
    }
  }

  def required[A : Read](long: String, metavar: String = "STRING", help: String = ""): Opts[A] =
    Opts(Opt.Regular(long, metavar, {
      case Nil => Parse.failure(s"Missing mandatory option: --$long")
      case first :: Nil => Read[A](first)
      case _ => Parse.failure(s"Too many values for option: --$long")
    }))

  def optional[A : Read](long: String, metavar: String = "STRING", help: String = ""): Opts[Option[A]] =
    Opts(Opt.Regular(long, metavar, {
      case Nil => Parse.success(None)
      case first :: Nil => Read[A](first).map(Some(_))
      case _ => Parse.failure(s"Too many values for option: --$long")
    }))

  def flag(long: String, help: String = ""): Opts[Boolean] =
    Opts(Opt.Flag(long, {
      case 0 => Parse.success(false)
      case _ => Parse.success(true)
    }))
}

sealed trait Opt[A] {

  def map[B](f: A => B): Opt[B] = this match {
    case opt: Opt.Regular[A] => opt.copy(converter = opt.converter andThen { _.map(f) } )
    case opt: Opt.Flag[A] => opt.copy(converter = opt.converter andThen { _.map(f) })
  }
}

object Opt {
  case class Regular[A](name: String, metavar: String, converter: List[String] => Parse.Result[A]) extends Opt[A]
  case class Flag[A](name: String, converter: Int => Parse.Result[A]) extends Opt[A]
}