package com.monovore.decline

class CommandApp(
  name: String,
  header: String,
  main: Opts[Unit],
  version: String = ""
) {

  def main(args: Array[String]): Unit = {

    val showVersion =
      if (version.isEmpty) Opts.never
      else
        Opts.flag("version", "Print the version number and exit.")
          .map { _ => System.err.println(version) }

    val command = Command(name, header, Opts.help orElse showVersion orElse main)

    Parse(args.toList, command.options)
      .valueOr { errors =>
        errors.foreach(System.err.println)
        System.err.println(Help.render(command))
      }
  }
}
