package com.monovore.decline.effect

import cats.ApplicativeError
import cats.effect.implicits._
import cats.effect.{Effect, ExitCode, IO, IOApp}
import cats.implicits._
import com.monovore.decline._

import scala.language.higherKinds

abstract class CommandIOApp[F[_]](
    name: String,
    header: String,
    helpFlag: Boolean = true,
    version: String = ""
)(implicit ev1: Effect[F], ev2: ApplicativeError[F, Throwable])
    extends IOApp {

  def main: Opts[F[_]]

  private[this] def command: Command[IO[ExitCode]] = {
    val mainCommand: Opts[IO[ExitCode]] =
      main.map(_.toIO.as(ExitCode.Success).handleError(_ => ExitCode.Error))

    val showVersion: Opts[IO[ExitCode]] = {
      if (version.isEmpty) Opts.never
      else {
        val flag = Opts.flag(
          long = "version",
          short = "v",
          help = "Print the version number and exit.",
          visibility = Visibility.Partial
        )
        flag.as(IO(System.out.println(version)).as(ExitCode.Success))
      }
    }

    Command(name, header, helpFlag)(showVersion orElse mainCommand)
  }

  override final def run(args: List[String]): IO[ExitCode] = {
    def printHelp(help: Help): IO[ExitCode] =
      IO(System.err.println(help)).as {
        if (help.errors.nonEmpty) ExitCode.Error
        else ExitCode.Success
      }

    for {
      parseResult <- IO(command.parse(PlatformApp.ambientArgs getOrElse args, sys.env))
      exitCode <- parseResult.fold(printHelp, identity)
    } yield exitCode
  }

}
