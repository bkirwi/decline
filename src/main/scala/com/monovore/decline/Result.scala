package com.monovore.decline

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import cats.{Alternative, Applicative, Eval, MonoidK, Semigroup}

private[decline] case class Result[+A](get: Eval[Result.Value[A]]) {
  def andThen[B](f: A => Result[B]): Result[B] = Result(get.flatMap {
    case Result.Return(a) => f(a).get
    case missing @ Result.Missing(_) => Eval.now(missing)
    case fail @ Result.Fail(_) => Eval.now(fail)
  })
}

private[decline] object Result {

  sealed trait Value[+A]

  case class Stuff(
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

  object Stuff {

    implicit val semigroup: Semigroup[Stuff] = new Semigroup[Stuff] {
      override def combine(x: Stuff, y: Stuff): Stuff =
        Stuff(
          x.flags ++ y.flags,
          x.commands ++ y.commands,
          x.argument || y.argument
        )
    }
  }

  case class Return[A](value: A) extends Value[A]
  case class Missing(flags: List[Stuff]) extends Value[Nothing]
  case class Fail(messages: List[String]) extends Value[Nothing]

  def success[A](value: A): Result[A] = Result(Eval.now(Return(value)))

  val missing = Result(Eval.now(Missing(Nil)))
  def missingFlag(flag: Opts.Name) = Result(Eval.now(Missing(List(Stuff(flags = List(flag))))))
  def missingCommand(command: String) = Result(Eval.now(Missing(List(Stuff(commands = List(command))))))
  def missingArgument = Result(Eval.now(Missing(List(Stuff(argument = true)))))

  def failure(messages: String*) = Result(Eval.now(Fail(messages.toList)))

  def fromValidated[A](validated: ValidatedNel[String, A]): Result[A] = validated match {
    case Validated.Valid(a) => success(a)
    case Validated.Invalid(errs) => failure(errs.toList: _*)
  }

  implicit val alternative: Alternative[Result] =
    new Alternative[Result] {

      override def pure[A](x: A): Result[A] = Result.success(x)

      override def ap[A, B](ff: Result[(A) => B])(fa: Result[A]): Result[B] = Result(
        (ff.get |@| fa.get).tupled.map {
          case (Return(f), Return(a)) => Return(f(a))
          case (Fail(l), Fail(r)) => Fail(l ++ r)
          case (Fail(l), Missing(r)) => Fail(l ++ r.map { _.message })
          case (Missing(l), Fail(r)) => Fail(l.map { _.message } ++ r)
          case (Fail(l), _) => Fail(l)
          case (_, Fail(r)) => Fail(r)
          case (Missing(l), Missing(r)) => Missing(l ++ r)
          case (Missing(l), _) => Missing(l)
          case (_, Missing(r)) => Missing(r)
        }
      )

      override def combineK[A](x: Result[A], y: Result[A]): Result[A] = Result(
        x.get.flatMap {
          case Missing(flags) => y.get.map {
            case Missing(moreFlags) => Missing((flags, moreFlags) match {
              case (Nil, x) => x
              case (x, Nil) => x
              case (x :: _, y :: _) => List(x |+| y)
            })
            case other => other
          }
          case other => Eval.now(other)
        }
      )

      override def empty[A]: Result[A] = missing
    }
}

