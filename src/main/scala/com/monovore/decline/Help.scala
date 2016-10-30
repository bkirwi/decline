package com.monovore.decline

private[decline] object Help {

  def render(parser: Command[_]): String = {

    val commands = commandList(parser.options)

    val commandUsage =
      if (commands.isEmpty) Nil else List("<command>", "[<args>]")

    val commandHelp =
      if (commands.isEmpty) Nil
      else s"Subcommands: ${ commands.map { _.name }.mkString(", ") }" ::
        commands.map(render)

    val optionsHelp = {
      val optionsDetail = detail(parser.options)
      if (optionsDetail.isEmpty) Nil
      else (s"Options and flags:" :: optionsDetail).mkString("\n") :: Nil
    }


    val parts = List(
      s"Usage: ${parser.name} ${(usage(parser.options) ++ args(parser.options) ++ commandUsage).mkString(" ")}",
      parser.header
    ) ++ optionsHelp ++ commandHelp

    parts.mkString("\n\n")
  }

  type Usage[A] = List[String]

  def optionList(opts: Opts[_]): List[Opts.Single[_, _]] = opts match {
    case Opts.Pure(_) => Nil
    case Opts.App(f, a) => optionList(f) ++ optionList(a)
    case single: Opts.Single[_, _] => List(single)
    case Opts.Validate(a, _) => optionList(a)
    case Opts.Subcommands(_) => Nil
  }

  def commandList(opts: Opts[_]): List[Command[_]] = opts match {
    case Opts.Subcommands(commands) => commands
    case Opts.App(f, a) => commandList(f) ++ commandList(a)
    case Opts.Validate(a, _) => commandList(a)
    case _ => Nil
  }

  def usage(opts: Opts[_]): List[String] =
    optionList(opts)
      .map { _.opt}
      .flatMap {
        case Opt.Regular(names, metavar) => s"[${names.head} <$metavar>]" :: Nil
        case Opt.Flag(names) => s"[${names.head}]" :: Nil
        case _ => Nil
      }

  def args(opts: Opts[_]): List[String] =
    optionList(opts)
      .map { _.opt }
      .flatMap {
        case Opt.Arguments(metavar, 1) => s"<$metavar>" :: Nil
        case Opt.Arguments(metavar, _) => s"<$metavar>..." :: Nil
        case _ => Nil
      }

  def detail(opts: Opts[_]): List[String] =
    optionList(opts)
      .flatMap {
        case Opts.Single(Opt.Regular(names, metavar), help) => List(
          s"    ${ names.map { name => s"$name <$metavar>"}.mkString(", ") }",
          s"            $help"
        )
        case Opts.Single(Opt.Flag(names), help) => List(
          s"    ${ names.mkString(", ") }",
          s"            $help"
        )
        case _ => Nil
      }
}
