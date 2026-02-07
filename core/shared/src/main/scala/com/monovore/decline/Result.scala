package com.monovore.decline

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.syntax.all._
import cats.{Alternative, Applicative, Semigroup}

private[decline] case class Result[+A](
    get: Validated[Result.Failure, () => Validated[List[String], A]],
    warnings: List[String] = Nil
) {

  def mapValidated[B](fn: A => Validated[List[String], B]): Result[B] =
    Result(get.map { _.map { _.andThen(fn) } }, warnings)

  def withWarning(warning: String): Result[A] =
    copy(warnings = warning :: warnings)

  def withError(error: String): Result[A] =
    Result(get.leftMap(_.withError(error)), warnings)
}

private[decline] object Result {

  case class Missing(
      flags: List[Opts.Name] = Nil,
      commands: List[String] = Nil,
      argument: Boolean = false,
      envVars: List[String] = Nil
  ) {

    def message: String = {
      val flagString =
        flags.distinct match {
          case Nil => None
          case one :: Nil => Some(s"flag $one")
          case more => Some(more.mkString("flag (", " or ", ")"))
        }

      val commandString =
        if (commands.isEmpty) None
        else Some(commands.distinct.mkString("command (", " or ", ")"))

      val argString = if (argument) Some("positional argument") else None

      val envVarString =
        if (envVars.isEmpty) None
        else Some(envVars.distinct.mkString("environment variable (", " or ", ")"))

      s"Missing expected ${List(flagString, commandString, argString, envVarString).flatten.mkString(", or ")}!"
    }
  }

  object Missing {

    implicit val semigroup: Semigroup[Missing] = new Semigroup[Missing] {
      override def combine(x: Missing, y: Missing): Missing =
        Missing(
          x.flags ++ y.flags,
          x.commands ++ y.commands,
          x.argument || y.argument,
          x.envVars ++ y.envVars
        )
    }
  }

  case class Failure(reversedMissing: List[Missing], errors: List[String] = Nil) {
    def messages: Seq[String] = errors.reverse ++ reversedMissing.reverse.map(_.message)
    def withError(error: String): Failure = copy(errors = error :: errors)
  }

  object Failure {
    implicit val failSemigroup: Semigroup[Failure] = new Semigroup[Failure] {
      override def combine(x: Failure, y: Failure): Failure =
        Failure(y.reversedMissing ++ x.reversedMissing, y.errors ++ x.errors)
    }
  }

  def success[A](value: A): Result[A] = Result(Validated.valid(() => Validated.valid(value)))

  val fail: Result[Nothing] = Result(Validated.invalid(Failure(Nil)))
  def missingFlag(flag: Opts.Name): Result[Nothing] =
    Result(Validated.invalid(Failure(List(Missing(flags = List(flag))))))
  def missingCommand(command: String): Result[Nothing] =
    Result(Validated.invalid(Failure(List(Missing(commands = List(command))))))
  def missingArgument: Result[Nothing] = Result(
    Validated.invalid(Failure(List(Missing(argument = true))))
  )
  def missingEnvVar(name: String): Result[Nothing] =
    Result(Validated.invalid(Failure(List(Missing(envVars = List(name))))))

  implicit val alternative: Alternative[Result] =
    new Alternative[Result] {

      private[this] val applicative =
        Applicative[Validated[Failure, *]].compose[Function0].compose[Validated[List[String], *]]

      override def pure[A](x: A): Result[A] = Result(applicative.pure(x))

      override def ap[A, B](ff: Result[A => B])(fa: Result[A]): Result[B] =
        Result(applicative.ap(ff.get)(fa.get), ff.warnings ++ fa.warnings)

      override def combineK[A](x: Result[A], y: Result[A]): Result[A] = (x, y) match {
        case (x0 @ Result(Valid(_), _), _) => x0
        case (_, y0 @ Result(Valid(_), _)) => y0
        case (x0, y0 @ Result(Invalid(Failure(Nil, Nil)), _)) => x0
        case (x0 @ Result(Invalid(Failure(Nil, Nil)), _), y0) => y0
        case (
              Result(Invalid(Failure(xMissing, xErrors)), xWarnings),
              Result(Invalid(Failure(yMissing, yErrors)), yWarnings)
            ) =>
          val merged = (xMissing zip yMissing).map { case (a, b) => a |+| b }
          Result(Invalid(Failure(merged, yErrors ++ xErrors)), xWarnings ++ yWarnings)
      }

      override def empty[A]: Result[A] = fail
    }
}
