package com.monovore.decline

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import cats.{Alternative, Applicative, Eval, MonoidK, Semigroup}

private[decline] case class Result[+A](get: Eval[Result.Value[A]]) {
  def andThen[B](f: A => Result[B]): Result[B] = Result(get.flatMap {
    case Result.Return(a) => f(a).get
    case missing @ Result.Fail(_) => Eval.now(missing)
    case fail @ Result.Halt(_) => Eval.now(fail)
  })
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

  // Success; Return the result
  case class Return[A](value: A) extends Value[A]
  // Soft failure; try other alternatives before returning an error. (eg. missing options or arguments)
  case class Fail(flags: List[Missing]) extends Value[Nothing]
  // Hard failure; bail out with the given messages. (eg. malformed arguments, validation failures
  case class Halt(messages: List[String]) extends Value[Nothing]

  def success[A](value: A): Result[A] = Result(Eval.now(Return(value)))

  val fail = Result(Eval.now(Fail(Nil)))
  def missingFlag(flag: Opts.Name) = Result(Eval.now(Fail(List(Missing(flags = List(flag))))))
  def missingCommand(command: String) = Result(Eval.now(Fail(List(Missing(commands = List(command))))))
  def missingArgument = Result(Eval.now(Fail(List(Missing(argument = true)))))

  def halt(messages: String*) = Result(Eval.now(Halt(messages.toList)))

  def fromValidated[A](validated: ValidatedNel[String, A]): Result[A] = validated match {
    case Validated.Valid(a) => success(a)
    case Validated.Invalid(errs) => halt(errs.toList: _*)
  }

  implicit val alternative: Alternative[Result] =
    new Alternative[Result] {

      override def pure[A](x: A): Result[A] = Result.success(x)

      override def ap[A, B](ff: Result[(A) => B])(fa: Result[A]): Result[B] = Result(
        (ff.get |@| fa.get).tupled.map {
          case (Return(f), Return(a)) => Return(f(a))
          case (Halt(l), Halt(r)) => Halt(l ++ r)
          case (Halt(l), Fail(r)) => Halt(l ++ r.map { _.message })
          case (Fail(l), Halt(r)) => Halt(l.map { _.message } ++ r)
          case (Halt(l), _) => Halt(l)
          case (_, Halt(r)) => Halt(r)
          case (Fail(l), Fail(r)) => Fail(l ++ r)
          case (Fail(l), _) => Fail(l)
          case (_, Fail(r)) => Fail(r)
        }
      )

      override def combineK[A](x: Result[A], y: Result[A]): Result[A] = Result(
        x.get.flatMap {
          case Fail(flags) => y.get.map {
            case Fail(moreFlags) => Fail((flags, moreFlags) match {
              case (Nil, x) => x
              case (x, Nil) => x
              case (x :: _, y :: _) => List(x |+| y)
            })
            case other => other
          }
          case other => Eval.now(other)
        }
      )

      override def empty[A]: Result[A] = fail
    }
}

