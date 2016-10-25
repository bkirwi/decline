package com.monovore.decline

import cats.Applicative
import cats.data.Validated
import cats.implicits._

object Parse {

  type Result[A] = Validated[List[String], A]

  def success[A](a: A): Result[A] = Validated.valid(a)

  def failure[A](reasons: String*): Result[A] = Validated.invalid(reasons.toList)

  trait Accumulator[A] {
    def parseOption(remaining: List[String]): Option[(Accumulator[A], List[String])]
    def parseArgs(remaining: List[String]): (Accumulator[A], List[String])
    def get: Result[A]
  }

  object Accumulator {

    val LongOpt = "--(.+)".r
    val LongEquals= "--([^=]+)=(.+)".r

    case class Pure[A](get: Result[A]) extends Accumulator[A] {
      override def parseOption(remaining: List[String]): Option[(Accumulator[A], List[String])] = None
      override def parseArgs(remaining: List[String]): (Accumulator[A], List[String]) = this -> remaining
    }

    case class App[X, A](left: Accumulator[X => A], right: Accumulator[X]) extends Accumulator[A] {

      override def parseOption(remaining: List[String]): Option[(Accumulator[A], List[String])] = {
        def maybeLeft = left.parseOption(remaining).map { case (newLeft, rest) => App(newLeft, right) -> rest }
        def maybeRight = right.parseOption(remaining).map { case (newRight, rest) => App(left, newRight) -> rest }
        maybeLeft orElse maybeRight
      }

      override def parseArgs(remaining: List[String]): (Accumulator[A], List[String]) = {
        val (newLeft, rest0) = left.parseArgs(remaining)
        val (newRight, rest1) = right.parseArgs(rest0)
        App(newLeft, newRight) -> rest1
      }

      override def get: Result[A] = (left.get |@| right.get).map { (f, a) => f(a) }
    }

    case class Regular(longFlag: String, values: List[String] = Nil) extends Accumulator[List[String]] {

      override def parseOption(remaining: List[String]): Option[(Accumulator[List[String]], List[String])] = remaining match {
        case LongOpt(`longFlag`) :: value :: rest => Some(copy(values = values :+ value) -> rest)
        case LongEquals(`longFlag`, value) :: rest => Some(copy(values = values :+ value) -> rest)
        case _ => None
      }

      override def parseArgs(remaining: List[String]): (Accumulator[List[String]], List[String]) = this -> remaining

      override def get: Result[List[String]] = success(values)
    }

    case class Flag(longFlag: String, values: Int = 0) extends Accumulator[Int] {

      override def parseOption(remaining: List[String]): Option[(Accumulator[Int], List[String])] = remaining match {
        case LongOpt(`longFlag`) :: rest => Some(copy(values = values + 1) -> rest)
        case _ => None
      }

      override def get: Result[Int] = success(values)

      override def parseArgs(remaining: List[String]): (Accumulator[Int], List[String]) = this -> remaining
    }

    case class Argument(limit: Int, value: List[String] = Nil) extends Accumulator[List[String]] {
      override def parseOption(remaining: List[String]): Option[(Accumulator[List[String]], List[String])] = None
      override def get: Result[List[String]] = success(value)

      override def parseArgs(remaining: List[String]): (Accumulator[List[String]], List[String]) = {
        val (taken, rest) = remaining.splitAt(limit)
        copy(value = taken) -> rest
      }
    }

    case class Validate[A, B](a: Accumulator[A], f: A => Result[B]) extends Accumulator[B] {
      override def parseOption(remaining: List[String]): Option[(Accumulator[B], List[String])] =
        a.parseOption(remaining).map { case (acc, rest) => Validate(acc, f) -> rest }
      override def get: Result[B] = a.get andThen f

      override def parseArgs(remaining: List[String]): (Accumulator[B], List[String]) = {
        val (a0, rest) = a.parseArgs(remaining)
        Validate(a0, f) -> rest
      }
    }

    implicit def applicative: Applicative[Accumulator] = new Applicative[Accumulator] {

      override def pure[A](x: A): Accumulator[A] =
        Pure(success(x))

      override def ap[A, B](ff: Accumulator[(A) => B])(fa: Accumulator[A]): Accumulator[B] =
        App(ff, fa)
    }

    def fromOpts[A](opts: Opts[A]): Accumulator[A] = opts match {
      case Opts.Pure(a) => Accumulator.Pure(Parse.success(a))
      case Opts.App(f, a) => Accumulator.App(fromOpts(f), fromOpts(a))
      case Opts.Validate(a, validation) => Validate(fromOpts(a), validation)
      case single: Opts.Single[A] => single.opt match {
        case Opt.Flag(name) => Flag(name)
        case Opt.Regular(name, _) => Regular(name)
        case Opt.Arguments(_, limit) => Argument(limit)
      }
    }

    def consume[A](args: List[String], accumulator: Accumulator[A]): Result[A] = {
      accumulator.parseOption(args)
        .map { case (next, rest) => consume(rest, next) }
        .getOrElse {

          val (postArgs, rest) = accumulator.parseArgs(args)

          if (rest.isEmpty) postArgs.get
          else failure(s"Unrecognized arguments: ${rest.mkString(" ")}")
        }
    }
  }

  def run[A](args: List[String], opts: Opts[A]): Result[A] = {
    val start = Accumulator.fromOpts(opts)
    Accumulator.consume(args, start)
  }
}