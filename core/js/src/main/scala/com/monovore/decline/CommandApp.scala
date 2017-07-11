package com.monovore.decline

import scala.scalajs.js
import scala.scalajs.js.annotation.JSGlobal

object CommandApp {

  @js.native
  @JSGlobal("process")
  private[CommandApp] object process extends js.Object {

    def argv: js.UndefOr[js.Array[String]] = js.native
  }
}

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

  def main(args: Array[String]): Unit = {

    // If running under node, grab those arguments
    val realArgs = CommandApp.process.argv.toOption.map { _.drop(2).toSeq }.getOrElse(args.toSeq)

    command.parse(realArgs) match {
      case Left(help) => System.err.println(help)
      case Right(_) => ()
    }

  }
}
