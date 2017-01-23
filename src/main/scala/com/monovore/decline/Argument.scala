package com.monovore.decline

import java.net.{URI, URISyntaxException}
import java.nio.file.{InvalidPathException, Path, Paths}

import cats.data.{Validated, ValidatedNel}

trait Argument[A] {
  def defaultMetavar: String
  def read(string: String): ValidatedNel[String, A]
}

object Argument {

  def apply[A](implicit argument: Argument[A]) = argument

  implicit val readString: Argument[String] = new Argument[String] {

    override def read(string: String): ValidatedNel[String, String] = Validated.valid(string)

    override def defaultMetavar: String = "string"
  }

  implicit val readInt: Argument[Int] = new Argument[Int] {

    override def read(string: String): ValidatedNel[String, Int] =
      try { Validated.valid(string.toInt) }
      catch { case nfe: NumberFormatException => Validated.invalidNel(s"Invalid integer: $string") }

    override def defaultMetavar: String = "integer"
  }

  implicit val readLong: Argument[Long] = new Argument[Long] {

    override def read(string: String): ValidatedNel[String, Long] =
      try { Validated.valid(string.toLong) }
      catch { case nfe: NumberFormatException => Validated.invalidNel(s"Invalid integer: $string") }

    override def defaultMetavar: String = "integer"
  }

  implicit val readURI: Argument[URI] = new Argument[URI] {

    override def read(string: String): ValidatedNel[String, URI] =
      try { Validated.valid(new URI(string)) }
      catch { case use: URISyntaxException => Validated.invalidNel(s"Invalid URI: $string (${ use.getReason })") }

    override def defaultMetavar: String = "uri"
  }

  implicit val readPath: Argument[Path] = new Argument[Path] {

    override def read(string: String): ValidatedNel[String, Path] =
      try { Validated.valid(Paths.get(string)) }
      catch { case ipe: InvalidPathException => Validated.invalidNel(s"Invalid path: $string (${ ipe.getReason })") }

    override def defaultMetavar: String = "path"
  }
}
