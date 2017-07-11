package com.monovore.decline

class CommandApp(command: Command[Unit]) {

  def this(
    name: String,
    header: String,
    main: Opts[Unit],
    helpFlag: Boolean = true,
    version: String = ""
  ) {

    this {
      val showVersion =
        if (version.isEmpty) Opts.never
        else
          Opts.flag("version", "Print the version number and exit.", visibility = Visibility.Partial)
            .map { _ => System.err.println(version) }

      Command(name, header, helpFlag)(showVersion orElse main)
    }
  }

  def main(args: Array[String]): Unit =
    command.parse(args) match {
      case Left(help) => System.err.println(help)
      case Right(_) => ()
    }
}
