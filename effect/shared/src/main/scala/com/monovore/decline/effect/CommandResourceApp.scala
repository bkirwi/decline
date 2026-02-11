package com.monovore.decline.effect

import cats.Functor
import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.Resource
import cats.effect.ResourceApp
import cats.effect.Sync
import cats.effect.std.Console
import cats.syntax.all._
import com.monovore.decline._

import scala.language.higherKinds

abstract class CommandResourceApp(
    name: String,
    header: String,
    helpFlag: Boolean = true,
    version: String = ""
) extends ResourceApp {

  def main: Opts[Resource[IO, ExitCode]]

  override final def run(args: List[String]): Resource[IO, ExitCode] =
    CommandResourceApp
      .run(name, header, helpFlag, Option(version).filter(_.nonEmpty))(main, args)

}

object CommandResourceApp {

  def run[F[_]: Sync: Console](
      name: String,
      header: String,
      helpFlag: Boolean = true,
      version: Option[String] = None
  )(opts: Opts[Resource[F, ExitCode]], args: List[String]): Resource[F, ExitCode] = {
    run[F](Command(name, header, helpFlag)(version.map(addVersionFlag(opts)).getOrElse(opts)), args)
  }

  def run[F[_]: Sync: Console](
      command: Command[Resource[F, ExitCode]],
      args: List[String]
  ): Resource[F, ExitCode] =
    for {
      parseResult <- Resource.eval(
        Sync[F].delay(
          command.parse(
            PlatformApp.ambientArgs getOrElse args,
            PlatformApp.ambientEnvs getOrElse sys.env
          )
        )
      )
      exitCode <- parseResult.fold(printHelp[F], identity)
    } yield exitCode

  private[CommandResourceApp] def printHelp[F[_]: Console: Functor](
      help: Help
  ): Resource[F, ExitCode] =
    Resource.eval(CommandIOApp.printHelp(help))

  private[CommandResourceApp] def addVersionFlag[F[_]: Console: Functor](
      opts: Opts[Resource[F, ExitCode]]
  )(version: String): Opts[Resource[F, ExitCode]] = {
    val flag = Opts.flag(
      long = "version",
      short = "v",
      help = "Print the version number and exit.",
      visibility = Visibility.Partial
    )

    flag.as(Resource.eval(Console[F].println(version).as(ExitCode.Success))) orElse opts
  }

}
