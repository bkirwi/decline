package com.monovore.decline

import cats.data.{Validated, ValidatedNel}
import java.net.{URI, URISyntaxException, URL, MalformedURLException}
import java.nio.file.{FileSystems, InvalidPathException, Path}
import java.util.UUID
import scala.concurrent.duration.Duration

trait Argument[A] { self =>
  def read(string: String): ValidatedNel[String, A]
}

object Argument extends PlatformArguments {

  def apply[A](implicit argument: Argument[A]) = argument

  def instance[A](f: String => ValidatedNel[String, A]): Argument[A] =
    new Argument[A] {
      def read(s: String): ValidatedNel[String, A] = f(s)
    }

  implicit val readString: Argument[String] =
    instance(s => Validated.valid(s))

  private def readNum[A](typeName: String)(parse: String => A): Argument[A] =
    instance { s =>
      try Validated.valid(parse(s))
      catch { case nfe: NumberFormatException => Validated.invalidNel(s"Invalid $typeName: $s") }
    }

  implicit val readBoolean: Argument[Boolean] =
    instance { s =>
      s.toLowerCase match {
        case "true" | "t" | "yes" | "y" => Validated.valid(true)
        case "false" | "f" | "no" | "n" => Validated.valid(false)
        case _ => Validated.invalidNel(s"Invalid boolean: $s")
      }
    }

  implicit val readChar: Argument[Char] =
    instance { s =>
      if (s.length == 1) Validated.valid(s.charAt(0))
      else Validated.invalidNel(s"invalid character: '$s'")
    }

  implicit val readByte: Argument[Byte] = readNum("integer")(_.toByte)
  implicit val readShort: Argument[Short] = readNum("integer")(_.toShort)
  implicit val readInt: Argument[Int] = readNum("integer")(_.toInt)
  implicit val readLong: Argument[Long] = readNum("integer")(_.toLong)
  implicit val readBigInt: Argument[BigInt] = readNum("integer")(BigInt(_))
  implicit val readBigInteger: Argument[java.math.BigInteger] = readNum("integer")(new java.math.BigInteger(_))

  implicit val readFloat: Argument[Float] = readNum("floating-point")(_.toFloat)
  implicit val readDouble: Argument[Double] = readNum("floating-point")(_.toDouble)

  implicit val readBigDecimal: Argument[BigDecimal] = readNum("decimal")(BigDecimal(_))
  implicit val readJavaBigDecimal: Argument[java.math.BigDecimal] = readNum("decimal")(new java.math.BigDecimal(_))

  implicit val readDuration: Argument[Duration] =
    Argument.instance { s =>
      try { Validated.valid(Duration(s)) }
      catch { case _: NumberFormatException => Validated.invalidNel(s"Invalid Duration: $s") }
    }

  implicit val readURI: Argument[URI] =
    Argument.instance { s =>
      try { Validated.valid(new URI(s)) }
      catch { case use: URISyntaxException => Validated.invalidNel(s"Invalid URI: $s (${ use.getReason })") }
    }

  implicit val readURL: Argument[URL] =
    Argument.instance { s =>
      try { Validated.valid(new URL(s)) }
      catch { case use: MalformedURLException => Validated.invalidNel(s"Invalid URL: $s (${ use.getMessage })") }
    }

  implicit val readUUID: Argument[UUID] =
    Argument.instance { s =>
      try { Validated.valid(UUID.fromString(s)) }
      catch { case _: IllegalArgumentException => Validated.invalidNel(s"Invalid UUID: $s") }
    }

  implicit val readNioPath: Argument[Path] =
    Argument.instance { s =>
      try { Validated.valid(FileSystems.getDefault.getPath(s)) }
      catch { case _: InvalidPathException => Validated.invalidNel(s"Invalid path: $s") }
    }
}
