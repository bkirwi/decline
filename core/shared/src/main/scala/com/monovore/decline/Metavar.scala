package com.monovore.decline

import scala.concurrent.duration.Duration
import scala.reflect.ClassTag

trait Metavar[A] {
  def name: String
}

object Metavar extends LowPriorityMetavar {
  def apply[A](implicit m: Metavar[A]) = m

  def instance[A](name0: String): Metavar[A] =
    new Metavar[A] {
      val name: String = name0
    }

  implicit val unitMetavar = Metavar.instance[Unit]("unit")
  implicit val booleanMetavar = Metavar.instance[Boolean]("boolean")

  implicit val byteMetavar = Metavar.instance[Byte]("integer")
  implicit val shortMetavar = Metavar.instance[Short]("integer")
  implicit val intMetavar = Metavar.instance[Int]("integer")
  implicit val longMetavar = Metavar.instance[Long]("integer")
  implicit val bigIntMetavar = Metavar.instance[BigInt]("integer")
  implicit val bigIntegerMetavar = Metavar.instance[java.math.BigInteger]("integer")

  implicit val floatMetavar = Metavar.instance[Float]("floating-point")
  implicit val doubleMetavar = Metavar.instance[Double]("floating-point")

  implicit val stringMetavar = Metavar.instance[String]("string")

  implicit val bigDecimalMetavar = Metavar.instance[BigDecimal]("decimal")
  implicit val javaBigDecimalMetavar = Metavar.instance[java.math.BigDecimal]("decimal")

  implicit val durationMetavar = Metavar.instance[Duration]("duration")

  // most of these are equivalent to using runtimeMetavar but it's
  // nice to include them in case we do want to change them.
  implicit val uriMetavar = Metavar.instance[java.net.URI]("uri")
  implicit val urlMetavar = Metavar.instance[java.net.URL]("url")
  implicit val currencydMetavar = Metavar.instance[java.util.Currency]("currency")
  implicit val dateMetavar = Metavar.instance[java.util.Date]("date")
  implicit val localeMetavar = Metavar.instance[java.util.Locale]("locale")
  implicit val timeZoneMetavar = Metavar.instance[java.util.TimeZone]("timezone")
  implicit val uuidMetavar = Metavar.instance[java.util.UUID]("uuid")
  implicit val pathMetavar = Metavar.instance[java.nio.file.Path]("path")
}

abstract class LowPriorityMetavar {

  val Basename = """(?:.+\.)([^.]+)$""".r

  implicit def runtimeMetavar[A](implicit ct: ClassTag[A]): Metavar[A] =
    Metavar.instance(ct.runtimeClass.getName match {
      case Basename(basename) => basename.toLowerCase
      case otherwise => otherwise
    })
}
