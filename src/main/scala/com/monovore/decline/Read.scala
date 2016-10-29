package com.monovore.decline

import java.net.{URI, URISyntaxException}
import java.nio.file.{InvalidPathException, Path, Paths}

trait Read[A] {
  def apply(string: String): Result[A]
}

object Read {

  def apply[A](string: String)(implicit read: Read[A]): Result[A] = read(string)

  implicit val readString: Read[String] = new Read[String] {
    override def apply(string: String): Result[String] = Result.success(string)
  }

  implicit val readInt: Read[Int] = new Read[Int] {
    override def apply(string: String): Result[Int] =
      try { Result.success(string.toInt) }
      catch { case nfe: NumberFormatException => Result.failure(s"Invalid integer: $string") }
  }

  implicit val readLong: Read[Long] = new Read[Long] {
    override def apply(string: String): Result[Long] =
      try { Result.success(string.toLong) }
      catch { case nfe: NumberFormatException => Result.failure(s"Invalid integer: $string") }
  }

  implicit val readURI: Read[URI] = new Read[URI] {
    override def apply(string: String): Result[URI] =
      try { Result.success(new URI(string)) }
      catch { case use: URISyntaxException => Result.failure(s"Invalid URI: $string (${ use.getReason })") }
  }

  implicit val readPath: Read[Path] = new Read[Path] {
    override def apply(string: String): Result[Path] =
      try { Result.success(Paths.get(string)) }
      catch { case ipe: InvalidPathException => Result.failure(s"Invalid path:$string (${ ipe.getReason })") }
  }
}
