package com.monovore.decline

import Usage._
import cats.implicits._

case class Usage(opts: Many[Options] = Prod(), args: Many[Args] = Prod()) {
  def show: List[String] = {
    val optStrings = showOptions(opts)
    val argStrings = showArgs(args)
    for {
      opt <- optStrings
      arg <- argStrings
    } yield (opt :+ arg).mkString(" ")
  }
}

object Usage {

  sealed trait Many[A] {
    def asProd: Prod[A] = Prod(this)
    def asSum: Sum[A] = Sum(this)
  }
  case class Just[A](value: A) extends Many[A] {
    override def toString: String = value.toString
  }
  case class Prod[A](allOf: Many[A]*) extends Many[A] {
    override def asProd: Prod[A] = this
    def and(other: Prod[A]): Prod[A] = Prod(allOf ++ other.allOf: _*)
    override def toString: String = allOf.mkString("{", " ", "}")
  }
  case class Sum[A](anyOf: Many[A]*) extends Many[A] {
    override def asSum: Sum[A] = this
    def or(other: Sum[A]): Sum[A] = Sum(anyOf ++ other.anyOf: _*)
    override def toString: String = anyOf.mkString("[", "|", "]")
  }

  // --foo bar [--baz | -quux <foo> [--quux foo]...]
  sealed trait Options
  object Options {
    case class Required(text: String) extends Options
    case class Repeated(text: String) extends Options
  }

  // <path> <path> [subcommand]
  // <path> [<string> [<integer>...]]
  sealed trait Args
  object Args {
    case class Required(metavar: String) extends Args
    case class Repeated(metavar: String) extends Args
    case class Command(name: String) extends Args
  }

  def single(opt: Opt[_]) = opt match {
    case Opt.Flag(names, _, Visibility.Normal) =>
      List(Usage(opts = Just(Options.Required(s"${names.head}"))))
    case Opt.Regular(names, metavar, _, Visibility.Normal) =>
      List(Usage(opts = Just(Options.Required(s"${names.head} <$metavar>"))))
    case Opt.Argument(metavar) =>
      List(Usage(args = Just(Args.Required(s"<$metavar>"))))
    case _ => List()
  }

  def repeated(opt: Opt[_]) = opt match {
    case Opt.Flag(names, _, Visibility.Normal) =>
      List(Usage(opts = Just(Options.Repeated(s"${names.head}"))))
    case Opt.Regular(names, metavar, _, Visibility.Normal) =>
      List(Usage(opts = Just(Options.Repeated(s"${names.head} <$metavar>"))))
    case Opt.Argument(metavar) =>
      List(Usage(args = Just(Args.Repeated(s"<$metavar>"))))
    case _ => List()
  }

  def asOptional[A](list: List[Many[A]]): Option[List[Many[A]]] = list match {
    case Nil =>  None
    case Prod() :: rest => Some(rest.filterNot { _ == Prod() })
    case other :: rest => asOptional(rest).map { other :: _ }
  }

  // [<a>] [<b>] --> [<a> [<b>]
  def showArgs(args: Many[Args]): List[String] = args match {
    case Prod() => List("")
    case Sum() => List()
    case Sum(single) => showArgs(single)
    case Prod(single) => showArgs(single)
    case Prod(many @ _*) => many.toList.traverse(showArgs).map { _.mkString(" ") }
    case Sum(many @ _*) => many.flatMap(showArgs).toList
    case Just(Args.Required(meta)) => List(meta)
    case Just(Args.Repeated(meta)) => List(s"$meta...")
    case Just(Args.Command(command)) => List(command)
  }

  def showOptions(opts: Many[Options]): List[List[String]] = opts match {
    case Sum(alternatives @ _*) => {
      val filtered = alternatives.filter { _ != Prod() }
      if (alternatives == filtered) alternatives.toList.flatMap(showOptions)
      else filtered match {
        case Seq(Just(Options.Repeated(a))) => List(List(s"[$a]..."))
        case _ => filtered.toList.traverse(showOptions).map { _.map { _.mkString(" ") }.mkString("[", " | ", "]") }.map(List(_))
      }
    }
    case Just(Options.Required(a)) => List(List(a))
    case Just(Options.Repeated(a)) => List(List(s"$a [$a]..."))
    case Prod(items @ _*) => items.toList.traverse(showOptions).map { _.flatten }
  }

  def fromOpts(opts: Opts[_]): List[Usage] = opts match {
    case Opts.Pure(result) =>
      result.get.value match {
        case Result.Return(_) => List(Usage())
        case _ => Nil
      }
    case Opts.Validate(more, _) => fromOpts(more)
    case Opts.Single(opt) => single(opt)
    case Opts.Repeated(opt) => repeated(opt)
    case Opts.Subcommand(command) => List(Usage(args = Just(Args.Command(command.name))))
    case Opts.App(left, right) =>
      for {
        l <- fromOpts(left)
        r <- fromOpts(right)
      } yield Usage(l.opts.asProd and r.opts.asProd, l.args.asProd and r.args.asProd)
    case Opts.OrElse(left, right) =>
      (fromOpts(left).reverse, fromOpts(right)) match {
        case (Usage(leftOpts, Prod()) :: ls, Usage(rightOpts, Prod()) :: rs) =>
          ls.reverse ++ List(Usage(opts = leftOpts.asSum or rightOpts.asSum)) ++ rs
        case (Usage(Prod(), leftArgs) :: ls, Usage(Prod(), rightArgs) :: rs) =>
          ls.reverse ++ List(Usage(args = leftArgs.asSum or rightArgs.asSum)) ++ rs
        case (ls, rs) => ls.reverse ++ rs
      }
  }
}