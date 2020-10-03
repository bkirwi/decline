package com.monovore.decline

/**
 * This abstract class takes a `Command[Unit]` and turns it into a main method for your application.
 * Normally, you want to extend this class from a top-level object:
 *
 * {{{
 * package myapp
 *
 * import com.monovore.decline._
 *
 * object MyApp extends CommandApp(
 *   name = "my-app",
 *   header = "This is a standalone application!",
 *   main =
 *     Opts.flag("fantastic", "Everything is working.")
 * )
 * }}}
 *
 * This should now behave like any other object with a main method -- for example, on the JVM, this
 * could be invoked as `java myapp.MyApp --fantastic`.
 */
abstract class CommandApp(command: Command[Unit], helpPrinter: Help.Printer) {

  def this(command: Command[Unit]) =
    this(command, Help.defaultPrinter)

  def this(
      name: String,
      header: String,
      main: Opts[Unit],
      helpFlag: Boolean = true,
      version: String = "",
      helpPrinter: Help.Printer = Help.defaultPrinter
  ) {

    this({
      val showVersion =
        if (version.isEmpty) Opts.never
        else
          Opts
            .flag("version", "Print the version number and exit.", visibility = Visibility.Partial)
            .map(_ => System.err.println(version))

      Command(name, header, helpFlag)(showVersion orElse main)
    }, helpPrinter)
  }

  @deprecated(
    """
The CommandApp.main method is not intended to be called by user code.
For suggested usage, see: http://monovore.com/decline/usage.html#defining-an-application""",
    "0.3.0"
  )
  final def main(args: Array[String]): Unit =
    command.parse(PlatformApp.ambientArgs getOrElse args, sys.env) match {
      case Left(help) => System.err.println(helpPrinter(help))
      case Right(_) => ()
    }
}
