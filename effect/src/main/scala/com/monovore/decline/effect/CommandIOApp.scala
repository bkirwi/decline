package com.monovore.decline.effect

import cats.effect.{ExitCode, IO, IOApp}
import cats.implicits._

import com.monovore.decline._

abstract class CommandIOApp[A](command: Command[A]) extends IOApp {

  def this(name: String, header: String, opts: Opts[A], helpFlag: Boolean = true, version: String = "") = {
    this {
      val showVersion = {
        if (version.isEmpty) Opts.never
        else Opts.info(version, "version", "Print the version number and exit.", visibility = Visibility.Partial)
      }
      Command(name, header, helpFlag)(showVersion orElse opts)
    }
  }

  def execute(a: A): IO[ExitCode]

  override final def run(args: List[String]): IO[ExitCode] = {
    def printMsg(info: ShowMsg): IO[ExitCode] =
      IO(System.out.println(info.msg)).as(ExitCode.Success)

    def printHelp(help: Help): IO[ExitCode] = {
      IO(System.err.println(help)).as(ExitCode.Error)
    }

    def handleAborted(reason: Aborted): IO[ExitCode] = reason match {
      case ShowHelp(help) => printHelp(help)
      case msg: ShowMsg   => printMsg(msg)
    }

    def runWithInput(input: A): IO[ExitCode] = {
      execute(input).handleErrorWith { err =>
        IO(err.printStackTrace()).as(ExitCode.Error)
      }
    }

    for {
      parseResult <- IO(command.parse(PlatformApp.ambientArgs getOrElse args, sys.env))
      execResult  <- parseResult.fold(handleAborted, runWithInput)
    } yield execResult
  }

}
