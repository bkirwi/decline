package com.monovore.decline

import java.net.{URI, URISyntaxException}
import java.nio.file.{InvalidPathException, Path, Paths}

trait Argument[A] {
  def defaultMetavar: String
  def read(string: String): Result[A]
}

object Argument {

  def apply[A](implicit argument: Argument[A]) = argument

  implicit val readString: Argument[String] = new Argument[String] {

    override def read(string: String): Result[String] = Result.success(string)

    override def defaultMetavar: String = "string"
  }

  implicit val readInt: Argument[Int] = new Argument[Int] {

    override def read(string: String): Result[Int] =
      try { Result.success(string.toInt) }
      catch { case nfe: NumberFormatException => Result.failure(s"Invalid integer: $string") }

    override def defaultMetavar: String = "integer"
  }

  implicit val readLong: Argument[Long] = new Argument[Long] {

    override def read(string: String): Result[Long] =
      try { Result.success(string.toLong) }
      catch { case nfe: NumberFormatException => Result.failure(s"Invalid integer: $string") }

    override def defaultMetavar: String = "integer"
  }

  implicit val readURI: Argument[URI] = new Argument[URI] {

    override def read(string: String): Result[URI] =
      try { Result.success(new URI(string)) }
      catch { case use: URISyntaxException => Result.failure(s"Invalid URI: $string (${ use.getReason })") }

    override def defaultMetavar: String = "uri"
  }

  implicit val readPath: Argument[Path] = new Argument[Path] {

    override def read(string: String): Result[Path] =
      try { Result.success(Paths.get(string)) }
      catch { case ipe: InvalidPathException => Result.failure(s"Invalid path: $string (${ ipe.getReason })") }

    override def defaultMetavar: String = "path"
  }
}
