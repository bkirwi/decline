package com.monovore.decline

import Usage._
import cats.implicits._
import cats.data.NonEmptyList

case class Usage(options: List[Options] = Nil, positional: Positional = Positional.Args(Args.Done)) {
  override def toString: String = {
    val positionalStrings = positional match {
      case Positional.Subcommands(List(command)) => List(command)
      case Positional.Subcommands(many) => many.mkString("[", " | ", "]") :: Nil
      case Positional.Args(args) => Args.show(args)
    }
    (options.map(Options.show) ++ positionalStrings).mkString(" ")
  }
}

object Usage {

  // --foo bar [--baz | -quux <foo> [--quux foo]...]
  sealed trait Options
  object Options {
    case class Required(text: String) extends Options
    case class Optional(alternatives: NonEmptyList[NonEmptyList[Options]]) extends Options
    case class Repeated(text: String) extends Options

    def show(options: Options): String = options match {
      case Optional(NonEmptyList(NonEmptyList(Repeated(text), Nil), Nil)) => s"[$text]..."
      case Optional(alts) =>
        alts.map { _.toList.map(Options.show).mkString(" ") }.toList.mkString("[", " | ", "]")
      case Repeated(text) => s"$text [$text]..."
      case Required(text) => text
    }

    def alternatives(options: List[Options]) = options match {
      case Nil => None
      case Optional(stuff) :: Nil => Some(stuff)
      case head :: tail => Some(NonEmptyList(NonEmptyList(head, tail), Nil))
    }
  }

  // <path> <path> [subcommand]
  // <path> [<string> [<integer>...]]
  sealed trait Args
  object Args {
    case class Required(metavar: String, next: Args) extends Args
    case class Optional(metavar: String, next: Args) extends Args
    case class Repeated(metavar: String) extends Args
    case object Done extends Args

    def show(args: Args): List[String] = args match {
      case Required(metavar, rest) => s"<$metavar>" :: show(rest)
      case Optional(metavar, rest) => (s"<$metavar>" :: show(rest)).mkString("[", " ", "]") :: Nil
      case Repeated(metavar) => s"<$metavar>..." :: Nil
      case Done => Nil
    }
  }

  sealed trait Positional
  object Positional {
    case class Args(args: Usage.Args) extends Positional
    case class Subcommands(commands: List[String]) extends Positional
  }

  type Usages = NonEmptyList[Usage]

  def single(opt: Opt[_]) = opt match {
    case Opt.Flag(names, _, _) =>
      List(Usage(options = List(Options.Required(s"${names.head}"))))
    case Opt.Regular(names, metavar, _, _) =>
      List(Usage(options = List(Options.Required(s"${names.head} <$metavar>"))))
    case Opt.Argument(metavar) =>
      List(Usage(positional = Positional.Args(Args.Required(metavar, Args.Done))))
  }

  def repeated(opt: Opt[_]) = opt match {
    case Opt.Flag(names, _, _) =>
      List(Usage(options = List(Options.Repeated(s"${names.head}"))))
    case Opt.Regular(names, metavar, _, _) =>
      List(Usage(options = List(Options.Repeated(s"${names.head} <$metavar>"))))
    case Opt.Argument(metavar) =>
      List(Usage(positional = Positional.Args(Args.Repeated(metavar))))
    case _ => List(Usage())
  }

  def args(left: Args, right: Args): Args = left match {
    case Args.Done => right
    case Args.Required(metavar, next) => Args.Required(metavar, args(next, right))
    case Args.Repeated(metavar) => Args.Repeated(metavar)
    case Args.Optional(metavar, next) => Args.Required(metavar, args(next, right))
  }

  def app(left: Usage, right: Usage) = {

    val positional = (left.positional, right.positional) match {
      case (Positional.Subcommands(l), Positional.Subcommands(r)) => Positional.Subcommands(l ++ r)
      case (commands: Positional.Subcommands, _) => commands
      case (_, commands: Positional.Subcommands) => commands
      case (Positional.Args(l), Positional.Args(r)) => Positional.Args(args(l, r))
    }

    Usage(left.options ++ right.options, positional)
  }

  def maybeCombine(left: Usage, right: Usage): Option[Usage] = (left, right) match {
    case (Usage(leftOptions, Positional.Args(Args.Done)), Usage(rightOptions, Positional.Args(Args.Done))) => {
      val options =
        (Options.alternatives(leftOptions) |+| Options.alternatives(rightOptions))
          .map(Options.Optional)
          .toList

      Some(Usage(options, Positional.Args(Args.Done)))
    }
    case _ => None
  }

  def fromOpts(opts: Opts[_]): List[Usage] = opts match {
    case Opts.Pure(result) => {
      result.get.value match {
        case Result.Return(_) => List(Usage())
        case _ => Nil
      }

    }
    case Opts.Validate(more, _) => fromOpts(more)
    case Opts.Single(opt) => single(opt)
    case Opts.Repeated(opt) => repeated(opt)
    case Opts.Subcommand(command) => List(Usage(positional = Positional.Subcommands(List(command.name))))
    case Opts.App(left, right) =>
      for {
        l <- fromOpts(left)
        r <- fromOpts(right)
      } yield app(l, r)
    case Opts.OrElse(left, right) => {
      // This is the hard case.
      (fromOpts(left), fromOpts(right)) match {
        case (List(a), List(b)) => maybeCombine(a, b).map { _ :: Nil }.getOrElse(a :: b :: Nil)
        case (as, bs) => as ++ bs
      }
    }
  }
}