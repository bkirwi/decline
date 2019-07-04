package com.monovore.decline

import cats.data.Validated.{Invalid, Valid}
import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import cats.{Alternative, Applicative, Semigroup}

private[decline] case class Result[+A](get: Validated[Result.Halt, () => Validated[List[String], A]]) {

  def mapValidated[B](fn: A => Validated[List[String], B]): Result[B] = Result(get.map { _.map { _.andThen(fn) } })

  def andHalt[B](reason: Result.Halt): Result[B] =
    Result(get.andThen(_ => reason.invalid[() => Validated[List[String], B]]))
}

private[decline] object Result {

  sealed trait Value[+A]

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

  case class Halt(info: Option[String], reversedMissing: List[Missing]) {
    def messages: Seq[String] = reversedMissing.reverse.map { _.message }
  }
  object Halt {
    implicit val haltSemigroup = new Semigroup[Halt] {
      override def combine(x: Halt, y: Halt): Halt =
        Halt(x.info |+| y.info, y.reversedMissing ++ x.reversedMissing)
    }
  }

  def success[A](value: A): Result[A] = Result(Validated.valid(() => Validated.valid(value)))

  val fail = Result(Validated.invalid(Halt(None, Nil)))
  def missingFlag(flag: Opts.Name) = Result(Validated.invalid(Halt(None, List(Missing(flags = List(flag))))))
  def missingCommand(command: String) = Result(Validated.invalid(Halt(None, List(Missing(commands = List(command))))))
  def missingArgument = Result(Validated.invalid(Halt(None, List(Missing(argument = true)))))
  def missingEnvVar(name: String) = Result(Validated.invalid(Halt(None, List(Missing(envVars = List(name))))))

  def halt(info: String) = Halt(Some(info), Nil)

  implicit val alternative: Alternative[Result] =
    new Alternative[Result] {

      private[this] val applicative = Applicative[Validated[Halt, ?]].compose[Function0].compose[Validated[List[String], ?]]

      override def pure[A](x: A): Result[A] = Result(applicative.pure(x))

      override def ap[A, B](ff: Result[A => B])(fa: Result[A]): Result[B] = Result(applicative.ap(ff.get)(fa.get))

      override def combineK[A](x: Result[A], y: Result[A]): Result[A] = (x, y) match {
        case (x0 @ Result(Valid(_)), _) => x0
        case (_, y0 @ Result(Valid(_))) => y0
        case (x0, Result(Invalid(Halt(None, Nil)))) => x0
        case (Result(Invalid(Halt(None, Nil))), y0) => y0
        case (Result(Invalid(Halt(xInfo, xMissing))), Result(Invalid(Halt(yInfo, yMissing)))) => {
          val merged = (xMissing zip yMissing).map { case (a, b) => a |+| b }
          Result(Invalid(Halt(xInfo |+| yInfo, merged)))
        }
      }

      override def empty[A]: Result[A] = fail
    }
}

