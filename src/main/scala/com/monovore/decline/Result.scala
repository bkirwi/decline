package com.monovore.decline

import cats.data.Validated.{Invalid, Valid}
import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import cats.{Alternative, Applicative, Semigroup}

private[decline] case class Result[+A](get: Validated[Result.Failure, () => Validated[List[String], A]]) {

  def mapValidated[B](fn: A => Validated[List[String], B]): Result[B] = Result(get.map { _.map { _.andThen(fn) } })
}

private[decline] object Result {

  sealed trait Value[+A]

  case class Missing(
    flags: List[Opts.Name] = Nil,
    commands: List[String] = Nil,
    argument: Boolean = false
  ) {

    def message: String = {
      val flagString =
        flags match {
          case Nil => None
          case one :: Nil => Some(s"flag $one")
          case _ => Some(flags.mkString("flag (", " or ", ")"))
        }

      val commandString =
        if (commands.isEmpty) None
        else Some(commands.mkString("command (", " or ", ")"))

      val argString = if (argument) Some("positional argument") else None

      s"Missing expected ${List(flagString, commandString, argString).flatten.mkString(", or ")}!"
    }
  }

  object Missing {

    implicit val semigroup: Semigroup[Missing] = new Semigroup[Missing] {
      override def combine(x: Missing, y: Missing): Missing =
        Missing(
          x.flags ++ y.flags,
          x.commands ++ y.commands,
          x.argument || y.argument
        )
    }
  }

  case class Failure(missing: List[Missing]) {
    def messages: Seq[String] = missing.map { _.message }
  }

  object Failure {
    implicit val failSemigroup = new Semigroup[Failure] {
      override def combine(x: Failure, y: Failure): Failure = Failure(x.missing ++ y.missing)
    }
  }

  def success[A](value: A): Result[A] = Result(Validated.valid(() => Validated.valid(value)))

  val fail = Result(Validated.invalid(Failure(Nil)))
  def missingFlag(flag: Opts.Name) = Result(Validated.invalid(Failure(List(Missing(flags = List(flag))))))
  def missingCommand(command: String) = Result(Validated.invalid(Failure(List(Missing(commands = List(command))))))
  def missingArgument = Result(Validated.invalid(Failure(List(Missing(argument = true)))))

  def halt(messages: String*) = Result(Validated.valid(() => Validated.invalid(messages.toList)))

  def fromValidated[A](validated: ValidatedNel[String, A]): Result[A] = validated match {
    case Validated.Valid(a) => success(a)
    case Validated.Invalid(errs) => halt(errs.toList: _*)
  }

  implicit val alternative: Alternative[Result] =
    new Alternative[Result] {

      private[this] val applicative = Applicative[Validated[Failure, ?]].compose[Function0].compose[Validated[List[String], ?]]

      override def pure[A](x: A): Result[A] = Result(applicative.pure(x))

      override def ap[A, B](ff: Result[(A) => B])(fa: Result[A]): Result[B] = Result(applicative.ap(ff.get)(fa.get))

      override def combineK[A](x: Result[A], y: Result[A]): Result[A] = (x, y) match {
        case (x0 @ Result(Valid(_)), _) => x0
        case (_, y0 @ Result(Valid(_))) => y0
        case (Result(Invalid(Failure(xMissing))), Result(Invalid(Failure(yMissing)))) => {
          def combine(left: List[Missing], right: List[Missing]): List[Missing] = (left, right) match {
            case (_, Nil) => left
            case (Nil, _) => right
            case (l :: lRest, r :: rRest) => (l |+| r) :: combine(lRest, rRest)
          }

          Result(Invalid(Failure(combine(xMissing, yMissing))))
        }
      }

      override def empty[A]: Result[A] = fail
    }
}

