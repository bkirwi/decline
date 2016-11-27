package com.monovore.decline

import cats.data.NonEmptyList

case class Help(
  errors: List[String],
  prefix: NonEmptyList[String],
  usage: List[String],
  body: List[String]
) {

  def withErrors(moreErrors: List[String]) = copy(errors = errors ++ moreErrors)

  def withPrefix(prefix: List[String]) = copy(prefix = prefix.foldRight(this.prefix) { _ :: _ })

  override def toString = {
    val maybeErrors = if (errors.isEmpty) None else Some(errors.mkString("\n"))
    val prefixString = prefix.toList.mkString(" ")
    val usageString = usage match {
      case Nil => s"Usage: $prefixString" // :(
      case only :: Nil => s"Usage: $prefixString $only"
      case _ => ("Usage:" :: usage.toList).mkString(s"\n    $prefixString ")
    }

    (maybeErrors.toList ++ List(usageString) ++ body).mkString("\n\n")
  }
}


object Help {

  def fromCommand(parser: Command[_]): Help = {

    val commands = commandList(parser.options)

    val commandHelp =
      if (commands.isEmpty) Nil
      else {
        val texts = commands.map { command => s"    ${command.name}\n        ${command.header}"}
        List((s"Subcommands:" +: texts).mkString("\n"))
      }

    val optionsHelp = {
      val optionsDetail = detail(parser.options)
      if (optionsDetail.isEmpty) Nil
      else (s"Options and flags:" :: optionsDetail).mkString("\n") :: Nil
    }

    Help(
      errors = Nil,
      prefix = NonEmptyList(parser.name, Nil),
      usage = Usage.fromOpts(parser.options).map { _.toString },
      body = List(parser.header) ++ optionsHelp ++ commandHelp
    )
  }

  def optionList(opts: Opts[_]): List[(Opt[_], Boolean)] = opts match {
    case Opts.Pure(_) => Nil
    case Opts.App(f, a) => optionList(f) ++ optionList(a)
    case Opts.OrElse(a, b) => optionList(a) ++ optionList(b)
    case Opts.Single(opt) => List(opt -> false)
    case Opts.Repeated(opt) => List(opt -> true)
    case Opts.Validate(a, _) => optionList(a)
    case Opts.Subcommand(_) => Nil
  }

  def commandList(opts: Opts[_]): List[Command[_]] = opts match {
    case Opts.Subcommand(command) => List(command)
    case Opts.App(f, a) => commandList(f) ++ commandList(a)
    case Opts.OrElse(f, a) => commandList(f) ++ commandList(a)
    case Opts.Validate(a, _) => commandList(a)
    case _ => Nil
  }

  def usage(opts: Opts[_]): List[String] =
    optionList(opts)
      .flatMap {
        case (Opt.Regular(names, metavar, _, Visibility.Normal), false) => s"[${names.head} <$metavar>]" :: Nil
        case (Opt.Flag(names, _, Visibility.Normal), false) => s"[${names.head}]" :: Nil
        case (Opt.Regular(names, metavar, _, Visibility.Normal), true) => s"[${names.head} <$metavar>]..." :: Nil
        case (Opt.Flag(names, _, Visibility.Normal), true) => s"[${names.head}]..." :: Nil
        case _ => Nil
      }

  def args(opts: Opts[_]): List[String] =
    optionList(opts)
      .flatMap {
        case (Opt.Argument(metavar), false) => s"<$metavar>" :: Nil
        case (Opt.Argument(metavar), true) => s"<$metavar>..." :: Nil
        case _ => Nil
      }

  def detail(opts: Opts[_]): List[String] =
    optionList(opts)
      .flatMap {
        case (Opt.Regular(names, metavar, help, _), _) => List(
          s"    ${ names.map { name => s"$name <$metavar>"}.mkString(", ") }",
          s"        $help"
        )
        case (Opt.Flag(names, help, _), _) => List(
          s"    ${ names.mkString(", ") }",
          s"        $help"
        )
        case _ => Nil
      }
}
