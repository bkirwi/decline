package com.monovore.clique

import cats.Applicative
import cats.free.FreeApplicative
import com.monovore.clique.Opt.Regular

import scala.util.{Failure, Success, Try}

/** A top-level argument parser, with all the info necessary to parse a full
  * set of arguments or display a useful help text.
  */
case class ArgParser[A](
  name: String = "program",
  header: String,
  options: Opts[A]
)

/** A parser for zero or more command-line options.
  */
case class Opts[A](value: FreeApplicative[Opt, A]) {
  def map[B](fn: A => B) = Opts(value.map(fn))
}

object Opts {

  type F[A] = FreeApplicative[Opt, A]

  // TODO: kittens!
  implicit val applicative: Applicative[Opts] = {
    val free = FreeApplicative.freeApplicative[Opt]
    new Applicative[Opts] {
      override def pure[A](x: A): Opts[A] = Opts(free.pure(x))
      override def ap[A, B](ff: Opts[A => B])(fa: Opts[A]): Opts[B] = Opts(free.ap(ff.value)(fa.value))
    }
  }

  def string(long: String, metavar: String = "STRING", help: String = ""): Opts[String] =
    Opts(FreeApplicative.lift(Opt.Regular(long, metavar, {
      case first :: Nil => Success(first)
      case _ => Failure(new IllegalArgumentException())
    })))
}

sealed trait Opt[A] {

  def map[B](f: A => B): Opt[B] = this match {
    case Regular(names, metavar, converter) => Regular(names, metavar, converter andThen { _.map(f) } )
  }
}

object Opt {
  case class Regular[A](name: String, metavar: String, converter: List[String] => Try[A]) extends Opt[A]
}