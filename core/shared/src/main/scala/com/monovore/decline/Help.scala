package com.monovore.decline

import cats.Show
import cats.data.NonEmptyList
import cats.syntax.all._
import com.monovore.decline.HelpRenderer.Plain
import com.monovore.decline.HelpRenderer.Colors

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

  implicit val declineHelpShow: Show[Help] =
    Show.fromToString[Help]

  def fromCommand(parser: Command[_]): Help = {
    fromCommand(parser, HelpRenderer.Plain)

  }

  def fromCommand(parser: Command[_], renderer: HelpRenderer): Help = {

    val theme = Theme.forRenderer(renderer)

    val commands = commandList(parser.options)

    val commandHelp =
      if (commands.isEmpty) Nil
      else {
        val texts = commands.flatMap { command =>
          List(withIndent(4, theme.subcommandName(command.name)), withIndent(8, command.header))
        }
        List((theme.sectionHeading("Subcommands:") :: texts).mkString("\n"))
      }

    val optionsHelp = {
      val optionsDetail = detail(parser.options, theme)
      if (optionsDetail.isEmpty) Nil
      else (theme.sectionHeading("Options and flags:") :: optionsDetail).mkString("\n") :: Nil
    }

    val envVarHelp = {
      val envVarHelpLines = environmentVarHelpLines(parser.options, theme).distinct
      if (envVarHelpLines.isEmpty) Nil
      else
        (theme.sectionHeading("Environment Variables:") :: envVarHelpLines.map("    " ++ _))
          .mkString("\n") :: Nil
    }

    Help(
      errors = Nil,
      prefix = NonEmptyList.of(parser.name),
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

  def environmentVarHelpLines(opts: Opts[_]): List[String] =
    environmentVarHelpLines(opts, PlainTheme)

  private def environmentVarHelpLines(opts: Opts[_], theme: Theme): List[String] = opts match {
    case Opts.Pure(_) => List()
    case Opts.Missing => List()
    case Opts.HelpFlag(a) => environmentVarHelpLines(a, theme)
    case Opts.App(f, a) => environmentVarHelpLines(f, theme) |+| environmentVarHelpLines(a, theme)
    case Opts.OrElse(a, b) =>
      environmentVarHelpLines(a, theme) |+| environmentVarHelpLines(b, theme)
    case Opts.Single(opt) => List()
    case Opts.Repeated(opt) => List()
    case Opts.Validate(a, _) => environmentVarHelpLines(a, theme)
    case Opts.Subcommand(_) => List()
    case Opts.Env(name, help, metavar) =>
      List(theme.envName(name) + s"=<$metavar>", withIndent(4, help))
  }

  def detail(opts: Opts[_]): List[String] = detail(opts, PlainTheme)

  private def detail(opts: Opts[_], theme: Theme): List[String] = {
    def optionName(name: String) = theme.optionName(name, Theme.ArgumentRenderingLocation.InOptions)
    def metavarName(name: String) = theme.metavar(name, Theme.ArgumentRenderingLocation.InOptions)

    optionList(opts)
      .getOrElse(Nil)
      .distinct
      .flatMap {
        case (Opt.Regular(names, metavar, help, _), _) =>
          List(
            withIndent(
              4,
              names.map(name => s"${optionName(name.toString)} <$metavar>").mkString(", ")
            ),
            withIndent(8, help)
          )
        case (Opt.Flag(names, help, _), _) =>
          List(
            withIndent(
              4,
              names
                .map(n => theme.optionName(n.toString(), Theme.ArgumentRenderingLocation.InOptions))
                .mkString(", ")
            ),
            withIndent(8, help)
          )
        case (Opt.OptionalOptArg(names, metavar, help, _), _) =>
          List(
            withIndent(
              4,
              names
                .map {
                  case Opts.ShortName(flag) => optionName(s"-$flag") + metavarName(s"[<$metavar>]")
                  case Opts.LongName(flag) => optionName(s"--$flag") + metavarName(s"[=<$metavar>]")
                }
                .mkString(", ")
            ),
            withIndent(8, help)
          )
        case (Opt.Argument(_), _) => Nil
      }
  }

  private def withIndent(indent: Int, s: String): String =
    // Predef.augmentString = work around scala/bug#11125
    augmentString(s).linesIterator.map(" " * indent + _).mkString("\n")
}
