package com.monovore.decline.effect

import cats.effect.{ExitCode, IO, IOApp, Sync}
import cats.implicits._

import com.monovore.decline._

import scala.language.higherKinds

abstract class CommandIOApp(
    name: String,
    header: String,
    helpFlag: Boolean = true,
    version: String = ""
) extends IOApp {

  def main: Opts[IO[ExitCode]]

  override final def run(args: List[String]): IO[ExitCode] = {
    import CommandIOApp._

    val opts = if (version.isEmpty) main else addVersionFlag(main)(version)
    val command = Command(name, header, helpFlag)(opts)

    for {
      parseResult <- IO(command.parse(PlatformApp.ambientArgs getOrElse args, sys.env))
      exitCode <- parseResult.fold(printHelp[IO], identity)
    } yield exitCode
  }

}

object CommandIOApp {

  def run[F[_]](
      name: String,
      header: String,
      helpFlag: Boolean = true,
      version: Option[String] = None
  )(opts: Opts[F[ExitCode]], args: List[String])(implicit F: Sync[F]): F[ExitCode] =
    run(Command(name, header, helpFlag)(version.map(addVersionFlag(opts)).getOrElse(opts)), args)

  def run[F[_]](command: Command[F[ExitCode]], args: List[String])(
      implicit F: Sync[F]
  ): F[ExitCode] =
    for {
      parseResult <- F.delay(command.parse(PlatformApp.ambientArgs getOrElse args, sys.env))
      exitCode <- parseResult.fold(printHelp[F], identity)
    } yield exitCode

  private[CommandIOApp] def printHelp[F[_]: Sync](help: Help): F[ExitCode] =
    Sync[F].delay(System.err.println(help)).as {
      if (help.errors.nonEmpty) ExitCode.Error
      else ExitCode.Success
    }

  private[CommandIOApp] def addVersionFlag[F[_]: Sync](
      opts: Opts[F[ExitCode]]
  )(version: String): Opts[F[ExitCode]] = {
    val flag = Opts.flag(
      long = "version",
      short = "v",
      help = "Print the version number and exit.",
      visibility = Visibility.Partial
    )

    flag.as(Sync[F].delay(System.out.println(version)).as(ExitCode.Success)) orElse opts
  }

}
