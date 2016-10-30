package com.monovore.decline

private[decline] object Help {

  def render(parser: Command[_]): String = {

    s"""Usage: ${parser.name} ${(usage(parser.options) ++ args(parser.options)).mkString(" ")}
       |
       |${parser.header}
       |
       |${detail(parser.options).mkString("\n")}
       |""".stripMargin
  }

  type Usage[A] = List[String]

  def flatten(opts: Opts[_]): List[Opts.Single[_, _]] = opts match {
    case Opts.Pure(_) => Nil
    case Opts.App(f, a) => flatten(f) ++ flatten(a)
    case single: Opts.Single[_, _] => List(single)
    case Opts.Validate(a, _) => flatten(a)
  }

  def usage(opts: Opts[_]): List[String] =
    flatten(opts)
      .map { _.opt}
      .flatMap {
        case Opt.Regular(names, metavar) => s"[${names.toList.sorted.head} <$metavar>]" :: Nil
        case Opt.Flag(names) => s"[${names.toList.sorted.head}]" :: Nil
        case _ => Nil
      }

  def args(opts: Opts[_]): List[String] =
    flatten(opts)
      .map { _.opt }
      .flatMap {
        case Opt.Arguments(metavar, 1) => s"<$metavar>" :: Nil
        case Opt.Arguments(metavar, _) => s"<$metavar>..." :: Nil
        case _ => Nil
      }

  def detail(opts: Opts[_]): List[String] =
    flatten(opts)
      .flatMap {
        case Opts.Single(Opt.Regular(names, metavar), help) => List(
          s"    ${ names.toList.sorted.map { name => s"$name <$metavar>"}.mkString(", ") }",
          s"            $help"
        )
        case Opts.Single(Opt.Flag(names), help) => List(
          s"    ${ names.toList.sorted.mkString(", ") }",
          s"            $help"
        )
        case _ => Nil
      }
}
