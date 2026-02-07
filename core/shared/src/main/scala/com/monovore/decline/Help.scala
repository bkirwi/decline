package com.monovore.decline

import cats.Show
import cats.data.NonEmptyList
import cats.syntax.all._
import RenderUtils._

class Help private (
    _prefix: NonEmptyList[String],
    args: HelpArgs
) extends Product
    with Serializable {

  def errors: List[String] = args.errors
  def warnings: List[String] = args.warnings

  def withErrors(moreErrors: List[String]): Help =
    copyArgs(args => args.copy(errors = args.errors ++ moreErrors))

  def withWarnings(moreWarnings: List[String]): Help =
    copyArgs(args => args.copy(warnings = args.warnings ++ moreWarnings))

  def withPrefix(prefix: List[String]): Help =
    new Help(_prefix = prefix.foldRight(this._prefix) { _ :: _ }, args = this.args)

  // we cache the rendering to avoid re-rendering every time a synthetic copy(...)
  // method is called
  private lazy val plainRendered = renderLines(Help.Plain)
  override def toString: String = {
    plainRendered.mkString(System.lineSeparator())
  }

  def render(format: HelpFormat): String =
    renderLines(format).mkString(System.lineSeparator())

  /**
   * Renders Help into string lines. Absence of line breaks within each individual element of the
   * returned list is not guarantee – e.g. if description contains line breaks, it won't be broken
   * into separate lines in the resulting list.
   *
   * @param format
   * @param options
   * @return
   */
  def renderLines(
      format: HelpFormat
  ): List[String] = {
    val theme = Theme.fromFormat(format)
    val lineSep = System.lineSeparator()

    import args._

    val commandSection =
      if (commandsHelp.isEmpty || !format.commandsEnabled) Nil
      else {
        val texts = commandsHelp.flatMap { command =>
          withIndent(4, command.show(theme))
        }
        List((theme.sectionHeading("Subcommands:") :: texts).mkString(lineSep))
      }

    val optionsSection = {
      if (optionHelp.isEmpty || !format.optionsEnabled) Nil
      else {
        val optionHelpLines =
          optionHelp.map(optHelp => withIndent(4, optHelp.show(theme))).flatten

        (theme.sectionHeading("Options and flags:") :: optionHelpLines).mkString(lineSep) :: Nil
      }
    }

    val envSection = {
      if (envHelp.isEmpty || !format.envEnabled) Nil
      else
        (theme.sectionHeading("Environment Variables:") :: envHelp
          .flatMap(_.show(theme))
          .map(withIndent(4, _)))
          .mkString(lineSep) :: Nil
    }

    val prefixString = _prefix.mkString_(" ")

    val usageSection =
      if (usages.isEmpty || !format.usageEnabled) Nil
      else {
        usages match {
          case Nil => List(theme.sectionHeading("Usage: ") + prefixString)
          case only :: Nil =>
            List(theme.sectionHeading("Usage: ") + s"$prefixString ${only.show.mkString(" ")}")
          case _ =>
            theme.sectionHeading("Usage:") :: usages.flatMap(us =>
              us.show.map(line => withIndent(4, prefixString + " " + line))
            )
        }
      }

    val hasWarnings = args.warnings.nonEmpty
    val hasErrors = args.errors.nonEmpty

    val errorsSection =
      if (!hasErrors || !format.errorsEnabled) Nil
      else args.errors.map(theme.error(_))

    val warningsSection =
      if (!hasWarnings || !format.warningsEnabled) Nil
      else {
        theme.sectionHeading("Warnings:") :: args.warnings.map(w => withIndent(4, theme.warning(w)))
      }

    val descriptionSection = if (format.descriptionEnabled) List(description) else Nil

    intersperseList(
      List(
        errorsSection,
        warningsSection,
        usageSection,
        descriptionSection,
        optionsSection,
        envSection,
        commandSection
      ).filter(_.nonEmpty),
      List("")
    ).flatten

  }

  private def copyArgs(modify: HelpArgs => HelpArgs) =
    new Help(args = modify(this.args), _prefix = this._prefix)

  /**
   * Prior to the addition of colors, the Help class had the following shape:
   *
   * case class Help( errors: List[String], prefix: NonEmptyList[String], usage: List[String], body:
   * List[String])
   *
   * To avoid breaking binary compatibility, we reintroduce the various synthetic methods it used to
   * have
   */
  @deprecated(
    "Help is no longer a case class, this constructor is preserved only for binary compatibility reason",
    "2.6.0"
  )
  def this(
      errors: List[String],
      prefix: NonEmptyList[String],
      usage: List[String],
      body: List[String]
  ) = this(
    prefix,
    HelpArgs(
      errors = errors,
      warnings = Nil,
      optionHelp = Nil,
      commandsHelp = Nil,
      envHelp = Nil,
      usages = Nil,
      description = ""
    )
  )

  // corresponds to `errors` field in old Help
  def `copy$default$1`: List[String] = args.errors

  // corresponds to `prefix` field in old Help
  def `copy$default$2`: NonEmptyList[String] = _prefix

  // corresponds to `usage` field in old Help
  // To avoid re-rendering usages every time copy(...) is called
  private lazy val renderedUsages = args.usages.flatMap(_.show)
  def `copy$default$3`: List[String] = renderedUsages

  // corresponds to `body` field in old Help
  def `copy$default$4`: List[String] = plainRendered

  def usage: List[String] = renderedUsages
  def prefix: NonEmptyList[String] = this._prefix
  def body: List[String] = this.plainRendered

  // case class specific methods
  def canEqual(o: Any): Boolean = o != null && o.isInstanceOf[Help]
  override def productPrefix: String = "Help"
  def productArity: Int = 4
  def productElement(n: Int): Any = n match {
    case 0 => errors
    case 1 => _prefix
    case 2 => renderedUsages
    case 3 => plainRendered
    case _ => throw new IndexOutOfBoundsException(n.toString)
  }
  def _1: List[String] = errors
  def _2: NonEmptyList[String] = _prefix
  def _3: List[String] = renderedUsages
  def _4: List[String] = plainRendered

  @deprecated(
    "This method has no effect and is only left for binary compatibility reasons",
    "2.6.0"
  )
  def copy(
      errors: List[String],
      prefix: NonEmptyList[String],
      usage: List[String],
      body: List[String]
  ): Help = this

}

object Help extends BinCompat {

  implicit val declineHelpShow: Show[Help] =
    Show.fromToString[Help]

  def fromCommand(parser: Command[_]): Help = {
    new Help(
      _prefix = NonEmptyList.of(parser.name),
      args = HelpArgs(
        errors = Nil,
        warnings = Nil,
        optionHelp = collectOptHelp(parser.options),
        commandsHelp = collectCommandHelp(parser.options),
        envHelp = collectEnvOptions(parser.options).distinct,
        usages = Usage.fromOpts(parser.options),
        description = parser.header
      )
    )
  }

  /**
   * Format help output with no colors
   */
  val Plain = HelpFormat.Plain

  /**
   * Format help output with colors and font decorations
   */
  val Colors = HelpFormat.Colors

  /**
   * Format that disables colors when a `NO_COLOR` environment variable is present.
   *
   * Example usage: `autoColors(sys.env)`
   *
   * @param env
   * @return
   */
  def autoColors(env: Map[String, String]) = HelpFormat.autoColors(env)

  @deprecated("Direct construction of Help class is prohibited", "2.6.0")
  def apply(
      errors: List[String],
      prefix: NonEmptyList[String],
      usage: List[String],
      body: List[String]
  ): Help = new Help(
    prefix,
    HelpArgs(
      errors = errors,
      warnings = Nil,
      optionHelp = Nil,
      commandsHelp = Nil,
      envHelp = Nil,
      usages = Nil,
      description = ""
    )
  )

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

  private[decline] def collectOptHelp(opts: Opts[_]): List[OptHelp] = {
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

  private[decline] def collectCommandHelp(opts: Opts[_]): List[CommandHelp] = opts match {
    case Opts.HelpFlag(a) => collectCommandHelp(a)
    case Opts.Subcommand(command) => List(CommandHelp(command.name, command.header))
    case Opts.App(f, a) => collectCommandHelp(f) ++ collectCommandHelp(a)
    case Opts.OrElse(f, a) => collectCommandHelp(f) ++ collectCommandHelp(a)
    case Opts.Validate(a, _) => collectCommandHelp(a)
    case _ => Nil
  }

  private[decline] def collectEnvOptions(opts: Opts[_]): List[EnvOptionHelp] =
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

  def environmentVarHelpLines(opts: Opts[_]): List[String] =
    environmentVarHelpLines(opts, PlainTheme)

  private[decline] def environmentVarHelpLines(opts: Opts[_], theme: Theme): List[String] =
    opts match {
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
  def detail(opts: Opts[_], theme: Theme): List[String] = {
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

  def commandList(opts: Opts[_]): List[Command[_]] = opts match {
    case Opts.HelpFlag(a) => commandList(a)
    case Opts.Subcommand(command) => List(command)
    case Opts.App(f, a) => commandList(f) ++ commandList(a)
    case Opts.OrElse(f, a) => commandList(f) ++ commandList(a)
    case Opts.Validate(a, _) => commandList(a)
    case _ => Nil
  }
}
