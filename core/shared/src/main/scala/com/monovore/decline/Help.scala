package com.monovore.decline

import cats.Show
import cats.data.NonEmptyList
import cats.syntax.all._
import com.monovore.decline.HelpFormat.Plain
import com.monovore.decline.HelpFormat.Colors

case class Help(
    errors: List[String],
    prefix: NonEmptyList[String],
    usage: List[String],
    body: List[String],
    args: Help.HelpArgs
) {

  def withErrors(moreErrors: List[String]) = copy(errors = errors ++ moreErrors)

  def withPrefix(prefix: List[String]) = copy(prefix = prefix.foldRight(this.prefix) { _ :: _ })

  override def toString = render(HelpFormat.Plain)

  def render(format: HelpFormat = HelpFormat.AutoColors()): String = {
    val theme = Theme.forRenderer(format)

    import args._
    import Help.withIndent

    val commandSection =
      if (commandsHelp.isEmpty) Nil
      else {
        val texts = commandsHelp.flatMap { command =>
          Help.withIndent(4, command.show(theme))
        }
        List((theme.sectionHeading("Subcommands:") :: texts).mkString("\n"))
      }

    def intersperseList[A](xs: List[A], x: A): List[A] = {
      val bld = List.newBuilder[A]
      val it = xs.iterator
      if (it.hasNext) {
        bld += it.next
        while (it.hasNext) {
          bld += x
          bld += it.next
        }
      }
      bld.result
    }

    val optionsSection = {
      val optionHelpLines =
        intersperseList(
          optionHelp.map(optHelp => withIndent(4, optHelp.show(theme))),
          List("")
        ).flatten

      if (optionHelp.isEmpty) Nil
      else (theme.sectionHeading("Options and flags:") :: optionHelpLines).mkString("\n") :: Nil
    }

    val envSection = {
      if (envHelp.isEmpty) Nil
      else
        (theme.sectionHeading("Environment Variables:") :: envHelp
          .flatMap(_.show(theme))
          .map(withIndent(4, _)))
          .mkString("\n") :: Nil
    }

    val prefixString = prefix.mkString_(" ")

    val usageSection = {
      theme.sectionHeading("Usage:") :: usages.flatMap(us =>
        us.show.map(line => withIndent(4, prefixString + " " + line))
      )
    }

    val errorsSection = if (args.errors.isEmpty) Nil else args.errors.map(theme.error(_))

    val descriptionSection = List(description)

    List(
      errorsSection.mkString("\n"),
      usageSection.mkString("\n"),
      descriptionSection.mkString("\n"),
      optionsSection.mkString("\n"),
      envSection.mkString("\n"),
      commandSection.mkString("\n")
    ).filter(_.nonEmpty)
      .mkString("\n\n")

  }

}

object Help {

  implicit val declineHelpShow: Show[Help] =
    Show.fromToString[Help]

  def fromCommand(parser: Command[_]): Help = {
    Help(
      errors = Nil,
      prefix = NonEmptyList.of(parser.name),
      usage = Nil,
      body = Nil,
      args = HelpArgs(
        errors = Nil,
        optionHelp = collectOptHelp(parser.options),
        commandsHelp = collectCommandHelp(parser.options),
        envHelp = collectEnvOptions(parser.options).distinct,
        usages = Usage.fromOpts(parser.options),
        description = parser.header
      )
    )

  }

  private[decline] case class HelpArgs(
      errors: List[String],
      optionHelp: List[OptHelp],
      commandsHelp: List[CommandHelp],
      envHelp: List[EnvOptionHelp],
      usages: List[Usage],
      description: String
  )

  private def optionList(opts: Opts[_]): Option[List[(Opt[_], Boolean)]] = opts match {
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

  private def collectOptHelp(opts: Opts[_]): List[OptHelp] = {
    optionList(opts).getOrElse(Nil).distinct.flatMap {
      case (Opt.Regular(names, metavar, help, _), _) =>
        Some(OptHelp(names.map { _.toString() -> Some(s" <$metavar>") }, help))
      case (Opt.Flag(names, help, _), _) =>
        Some(OptHelp(names.map(n => n.toString -> None), help))
      case (Opt.OptionalOptArg(names, metavar, help, _), _) =>
        Some(
          OptHelp(
            names
              .map {
                case Opts.ShortName(flag) => s"-$flag" -> Some(s"[<$metavar>]")
                case Opts.LongName(flag) => s"--$flag" -> Some(s"[=<$metavar>]")
              },
            help
          )
        )
      case (Opt.Argument(_), _) => None

    }
  }

  private def collectCommandHelp(opts: Opts[_]): List[CommandHelp] = opts match {
    case Opts.HelpFlag(a) => collectCommandHelp(a)
    case Opts.Subcommand(command) => List(CommandHelp(command.name, command.header))
    case Opts.App(f, a) => collectCommandHelp(f) ++ collectCommandHelp(a)
    case Opts.OrElse(f, a) => collectCommandHelp(f) ++ collectCommandHelp(a)
    case Opts.Validate(a, _) => collectCommandHelp(a)
    case _ => Nil
  }

  private def collectEnvOptions(opts: Opts[_]): List[EnvOptionHelp] =
    opts match {
      case Opts.Pure(_) => List()
      case Opts.Missing => List()
      case Opts.HelpFlag(a) => collectEnvOptions(a)
      case Opts.App(f, a) => collectEnvOptions(f) |+| collectEnvOptions(a)
      case Opts.OrElse(a, b) =>
        collectEnvOptions(a) |+| collectEnvOptions(b)
      case Opts.Single(opt) => List()
      case Opts.Repeated(opt) => List()
      case Opts.Validate(a, _) => collectEnvOptions(a)
      case Opts.Subcommand(_) => List()
      case Opts.Env(name, help, metavar) =>
        List(EnvOptionHelp(name = name, metavar = metavar, help = help))
    }

  private def environmentVarHelpLines(opts: Opts[_]): List[String] =
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

  private def detail(opts: Opts[_]): List[String] = detail(opts, PlainTheme)
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
              names
                .map(name => s"${optionName(name.toString)} ${metavarName(s"<$metavar>")}")
                .mkString(", ")
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

  private def withIndent(indent: Int, lines: List[String]): List[String] =
    lines.map(line => withIndent(indent, line))

  private[decline] case class OptHelp(variants: List[(String, Option[String])], help: String) {
    def show(theme: Theme): List[String] = {
      val newValue = Theme.ArgumentRenderingLocation.InOptions

      val argLine = variants
        .map { case (name, metavarOpt) =>
          theme.optionName(name, newValue) + metavarOpt
            .map { metavar =>
              val spaces = metavar.takeWhile(_.isWhitespace).length
              (" " * spaces) + theme.metavar(metavar.trim, newValue)
            }
            .getOrElse("")
        }
        .mkString(", ")

      List(argLine, withIndent(4, help))
    }
  }

  private[decline] case class EnvOptionHelp(name: String, metavar: String, help: String) {
    def show(theme: Theme): List[String] =
      List(theme.envName(name) + s"=<$metavar>", withIndent(4, help))
  }
  private[decline] case class CommandHelp(name: String, help: String) {
    def show(theme: Theme): List[String] =
      List(theme.subcommandName(name), withIndent(4, help))
  }
}
