package com.monovore.decline

import java.nio.file.{InvalidPathException, Path, Paths}

import cats.data.{Validated, ValidatedNel}

abstract class PlatformArguments {

  implicit val readPath: Argument[Path] = new Argument[Path] {

    override def read(string: String): ValidatedNel[String, Path] =
      try { Validated.valid(Paths.get(string)) }
      catch { case ipe: InvalidPathException => Validated.invalidNel(s"Invalid path: $string (${ ipe.getReason })") }

    override def defaultMetavar: String = "path"
  }
}
