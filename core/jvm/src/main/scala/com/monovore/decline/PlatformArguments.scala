package com.monovore.decline

import cats.data.{Validated, ValidatedNel}

import java.nio.file.{InvalidPathException, Path, Paths}
import java.time.temporal.ChronoUnit

private[decline] abstract class PlatformArguments {

  implicit val readPath: Argument[Path] = new Argument[Path] {

    override def read(string: String): ValidatedNel[String, Path] =
      try {
        Validated.valid(Paths.get(string))
      } catch {
        case ipe: InvalidPathException =>
          Validated.invalidNel(s"Invalid path: $string (${ipe.getReason})")
      }

    override def defaultMetavar: String = "path"
  }

  implicit val readChronoUnit: Argument[ChronoUnit] = new Argument[ChronoUnit] {

    override def read(string: String): ValidatedNel[String, ChronoUnit] =
      try {
        Validated.valid(ChronoUnit.valueOf(string.toUpperCase.replace('-', '_')))
      } catch {
        case _: IllegalArgumentException =>
          Validated.invalidNel(s"Invalid time unit: $string")
      }

    override def defaultMetavar: String = "time unit"
  }
}
