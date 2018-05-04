package com.monovore.decline

import java.nio.file.{InvalidPathException, Path, Paths}
import java.net.{URL, MalformedURLException}

import cats.data.{Validated, ValidatedNel}

private[decline] abstract class PlatformArguments {

  implicit lazy val readPath: Argument[Path] =
    Argument.instance { s =>
      try { Validated.valid(Paths.get(s)) }
      catch { case ipe: InvalidPathException => Validated.invalidNel(s"Invalid path: $s (${ ipe.getReason })") }
    }

  implicit lazy val readURL: Argument[URL] =
    Argument.instance { s =>
      try { Validated.valid(new URL(s)) }
      catch { case use: MalformedURLException => Validated.invalidNel(s"Invalid URL: $s (${ use.getMessage })") }
    }

}
