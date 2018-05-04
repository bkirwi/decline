package com.monovore.decline

import java.net.{URI, URISyntaxException}

import cats.data.{Validated, ValidatedNel}

trait Argument[A] { self =>
  def read(string: String): ValidatedNel[String, A]
}

object Argument extends PlatformArguments {

  def apply[A](implicit argument: Argument[A]) = argument

  implicit val readString: Argument[String] = new Argument[String] {

    override def read(string: String): ValidatedNel[String, String] = Validated.valid(string)
  }

  private def readNum[A](typeName: String)(parse: String => A): Argument[A] = new Argument[A] {
    override def read(string: String): ValidatedNel[String, A] =
      try Validated.valid(parse(string))
      catch { case nfe: NumberFormatException => Validated.invalidNel(s"Invalid $typeName: $string") }
  }

  implicit val readInt: Argument[Int] = readNum("integer")(_.toInt)
  implicit val readLong: Argument[Long] = readNum("integer")(_.toLong)
  implicit val readBigInt: Argument[BigInt] = readNum("integer")(BigInt(_))
  implicit val readFloat: Argument[Float] = readNum("floating-point")(_.toFloat)
  implicit val readDouble: Argument[Double] = readNum("floating-point")(_.toDouble)
  implicit val readBigDecimal: Argument[BigDecimal] = readNum("decimal")(BigDecimal(_))

  implicit val readURI: Argument[URI] = new Argument[URI] {

    override def read(string: String): ValidatedNel[String, URI] =
      try { Validated.valid(new URI(string)) }
      catch { case use: URISyntaxException => Validated.invalidNel(s"Invalid URI: $string (${ use.getReason })") }
  }
}
