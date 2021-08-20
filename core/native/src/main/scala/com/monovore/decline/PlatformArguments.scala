package com.monovore.decline

import java.nio.file.{Path, Paths}

import cats.data.{Validated, ValidatedNel}

private[decline] abstract class PlatformArguments {

  implicit val readPath: Argument[Path] = new Argument[Path] {

    override def read(string: String): ValidatedNel[String, Path] =
      try {
        Validated.valid(Paths.get(string))
      } catch {
        // note: scala-native is missing InvalidPathException
        case iae: IllegalArgumentException =>
          Validated.invalidNel(s"Invalid path: $string")
      }

    override def defaultMetavar: String = "path"
  }
}
