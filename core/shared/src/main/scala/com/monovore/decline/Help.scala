package com.monovore.decline

import cats.data.NonEmptyList
import cats.implicits._

case class Help(
    errors: List[String],
    prefix: NonEmptyList[String],
    usage: List[String],
    body: List[String]
) {

  def withErrors(moreErrors: List[String]) = copy(errors = errors ++ moreErrors)

  def withPrefix(prefix: List[String]) = copy(prefix = prefix.foldRight(this.prefix) { _ :: _ })

  override def toString = {
    val maybeErrors = if (errors.isEmpty) Nil else List(errors.mkString("\n"))
    val prefixString = prefix.toList.mkString(" ")
    val usageString = usage match {
      case Nil => s"Usage: $prefixString" // :(
      case only :: Nil => s"Usage: $prefixString $only"
      case _ => ("Usage:" :: usage).mkString(s"\n    $prefixString ")
    }

    (maybeErrors ::: (usageString :: body)).mkString("\n\n")
  }
}

object Help {

  def fromCommand(parser: Command[_]): Help = {

    val commands = commandList(parser.options)

    val commandHelp =
      if (commands.isEmpty) Nil
      else {
        val texts = commands.flatMap { command =>
          List(withIndent(4, command.name), withIndent(8, command.header))
        }
        List(("Subcommands:" :: texts).mkString("\n"))
      }

    val optionsHelp = {
      val optionsDetail = detail(parser.options)
      if (optionsDetail.isEmpty) Nil
      else ("Options and flags:" :: optionsDetail).mkString("\n") :: Nil
    }

    val envVarHelp = {
      val envVarHelpLines = environmentVarHelpLines(parser.options).distinct
      if (envVarHelpLines.isEmpty) Nil
      else ("Environment Variables:" :: envVarHelpLines.map("    " ++ _)).mkString("\n") :: Nil
    }

    Help(
      errors = Nil,
      prefix = NonEmptyList(parser.name, Nil),
      usage = Usage.fromOpts(parser.options).flatMap { _.show },
      body = parser.header :: (optionsHelp ::: envVarHelp ::: commandHelp)
    )
  }

  def optionList(opts: Opts[_]): Option[List[(Opt[_], Boolean)]] = opts match {
    case Opts.Pure(_) => Some(Nil)
    case Opts.Missing => None
    case Opts.HelpFlag(a) => optionList(a)
    case Opts.App(f, a) => (optionList(f), optionList(a)).mapN { _ ++ _ }
    case Opts.OrElse(a, b) => optionList(a) |+| optionList(b)
    case Opts.Single(opt) => Some(List(opt -> false))
    case Opts.Repeated(opt) => Some(List(opt -> true))
    case Opts.Validate(a, _) => optionList(a)
    case Opts.Subcommand(_) => Some(Nil)
    case Opts.Env(_, _, _) => Some(Nil)
  }

  def commandList(opts: Opts[_]): List[Command[_]] = opts match {
    case Opts.HelpFlag(a) => commandList(a)
    case Opts.Subcommand(command) => List(command)
    case Opts.App(f, a) => commandList(f) ++ commandList(a)
    case Opts.OrElse(f, a) => commandList(f) ++ commandList(a)
    case Opts.Validate(a, _) => commandList(a)
    case _ => Nil
  }

  def environmentVarHelpLines(opts: Opts[_]): List[String] = opts match {
    case Opts.Pure(_) => List()
    case Opts.Missing => List()
    case Opts.HelpFlag(a) => environmentVarHelpLines(a)
    case Opts.App(f, a) => environmentVarHelpLines(f) |+| environmentVarHelpLines(a)
    case Opts.OrElse(a, b) => environmentVarHelpLines(a) |+| environmentVarHelpLines(b)
    case Opts.Single(opt) => List()
    case Opts.Repeated(opt) => List()
    case Opts.Validate(a, _) => environmentVarHelpLines(a)
    case Opts.Subcommand(_) => List()
    case Opts.Env(name, help, metavar) => List(s"$name=<$metavar>", withIndent(4, help))
  }

  def detail(opts: Opts[_]): List[String] =
    optionList(opts)
      .getOrElse(Nil)
      .distinct
      .flatMap {
        case (Opt.Regular(names, metavar, help, _), _) =>
          List(
            withIndent(4, names.map(name => s"$name <$metavar>").mkString(", ")),
            withIndent(8, help)
          )
        case (Opt.Flag(names, help, _), _) =>
          List(
            withIndent(4, names.mkString(", ")),
            withIndent(8, help)
          )
        case _ => Nil
      }

  private def withIndent(indent: Int, s: String): String =
    // Predef.augmentString = work around scala/bug#11125
    augmentString(s).linesIterator.map(" " * indent + _).mkString("\n")
}
