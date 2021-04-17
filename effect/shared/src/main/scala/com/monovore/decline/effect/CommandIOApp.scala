package com.monovore.decline.effect

import cats.effect.{ExitCode, IO, IOApp, Sync}
import cats.effect.std.Console
import cats.syntax.all._

import com.monovore.decline._
import cats.effect.std.Console
import scala.language.higherKinds
import cats.Functor

abstract class CommandIOApp(
    name: String,
    header: String,
    helpFlag: Boolean = true,
    version: String = ""
) extends IOApp {

  def main: Opts[IO[ExitCode]]

  override final def run(args: List[String]): IO[ExitCode] =
    CommandIOApp.run[IO](name, header, helpFlag, Option(version).filter(_.nonEmpty))(main, args)

}

object CommandIOApp {

  def run[F[_]: Sync: Console](
      name: String,
      header: String,
      helpFlag: Boolean = true,
      version: Option[String] = None
  )(opts: Opts[F[ExitCode]], args: List[String]): F[ExitCode] =
    run(Command(name, header, helpFlag)(version.map(addVersionFlag(opts)).getOrElse(opts)), args)

  def run[F[_]: Sync: Console](command: Command[F[ExitCode]], args: List[String]): F[ExitCode] =
    for {
      parseResult <- Sync[F].delay(command.parse(PlatformApp.ambientArgs getOrElse args, sys.env))
      exitCode <- parseResult.fold(printHelp[F], identity)
    } yield exitCode

  private[CommandIOApp] def printHelp[F[_]: Console: Functor](help: Help): F[ExitCode] =
    Console[F].errorln(help).as {
      if (help.errors.nonEmpty) ExitCode.Error
      else ExitCode.Success
    }

  private[CommandIOApp] def addVersionFlag[F[_]: Console: Functor](
      opts: Opts[F[ExitCode]]
  )(version: String): Opts[F[ExitCode]] = {
    val flag = Opts.flag(
      long = "version",
      short = "v",
      help = "Print the version number and exit.",
      visibility = Visibility.Partial
    )

    flag.as(Console[F].println(version).as(ExitCode.Success)) orElse opts
  }

}
