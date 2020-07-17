package com.monovore.decline

import java.net.{URI, URISyntaxException}
import java.util.UUID

import cats.{Eval, Functor, SemigroupK}
import cats.data.{Validated, ValidatedNel}

import scala.annotation.implicitNotFound

/**
 * This typeclass captures the information needed to use this type as an option argument.
 *
 * See the [[http://monovore.com/decline/arguments.html documentation]] for more details.
 */
@implicitNotFound(
  "No Argument instance found for ${A}. For more info, see: http://monovore.com/decline/arguments.html#missing-instances"
)
trait Argument[+A] { self =>

  /**
   * Attempt to parse a single command-line argument: given an argument, this returns either
   * the parsed value or a message explaining the failure.
   */
  def read(string: String): ValidatedNel[String, A]

  /**
   * Returns a short, human-readable description of the accepted input format for this type,
   * suitable to be used in a command-line usage message.
   */
  def defaultMetavar: String

  override def toString: String = s"Argument(<$defaultMetavar>)"
}

object Argument extends PlatformArguments {

  def apply[A](implicit argument: Argument[A]): Argument[A] = argument

  /**
   * We can't add methods to traits in 2.11 without breaking binary compatibility
   */
  implicit final class ArgumentMethods[A](private val self: Argument[A]) extends AnyVal {
    def map[B](fn: A => B): Argument[B] =
      new Argument[B] {
        def read(string: String): ValidatedNel[String, B] =
          self.read(string).map(fn)

        def defaultMetavar = self.defaultMetavar
      }

    def mapValidated[B](function: A => ValidatedNel[String, B]): Argument[B] =
      new Argument[B] {
        def read(string: String): ValidatedNel[String, B] =
          self
            .read(string)
            .andThen(function)

        def defaultMetavar = self.defaultMetavar
      }
  }

  implicit val readString: Argument[String] = new Argument[String] {

    override def read(string: String): ValidatedNel[String, String] = Validated.valid(string)

    override def defaultMetavar: String = "string"
  }

  private def readNum[A](typeName: String)(parse: String => A): Argument[A] = new Argument[A] {
    override def read(string: String): ValidatedNel[String, A] =
      try Validated.valid(parse(string))
      catch {
        case nfe: NumberFormatException => Validated.invalidNel(s"Invalid $typeName: $string")
      }

    override def defaultMetavar: String = typeName
  }

  implicit val declineArgumentFunctor: Functor[Argument] =
    new Functor[Argument] {
      def map[A, B](arga: Argument[A])(fn: A => B): Argument[B] =
        arga.map(fn)
    }

  implicit val declineArgumentSemigroupK: SemigroupK[Argument] =
    new SemigroupK[Argument] {
      override def combineK[A](x: Argument[A], y: Argument[A]): Argument[A] =
        new Argument[A] {
          override def read(string: String): ValidatedNel[String, A] = {
            val ax = x.read(string)
            if (ax.isValid) ax
            else y.read(string)
          }

          override def defaultMetavar: String = s"${x.defaultMetavar}-or-${y.defaultMetavar}"
        }

      /*
     * when we bump cats 2.2 un-comment this code
     *
      def combineKEval[A](x: Argument[A], y: Eval[Argument[A]]): Eval[Argument[A]] =
        Eval.now(new Argument[A] {
          override def read(string: String): ValidatedNel[String, A] = {
            val ax = x.read(string)
            if (ax.isValid) ax
            else y.value.read(string)
          }

          override def defaultMetavar: String = s"${x.defaultMetavar}-or-${y.value.defaultMetavar}"
        })
     */
    }

  implicit val readInt: Argument[Int] = readNum("integer")(_.toInt)
  implicit val readLong: Argument[Long] = readNum("integer")(_.toLong)
  implicit val readShort: Argument[Short] = readNum("integer")(_.toShort)
  implicit val readBigInt: Argument[BigInt] = readNum("integer")(BigInt(_))
  implicit val readFloat: Argument[Float] = readNum("floating-point")(_.toFloat)
  implicit val readDouble: Argument[Double] = readNum("floating-point")(_.toDouble)
  implicit val readBigDecimal: Argument[BigDecimal] = readNum("decimal")(BigDecimal(_))
  implicit val readByte: Argument[Byte] = readNum("byte")(_.toByte)

  implicit val readChar: Argument[Char] = new Argument[Char] {
    override def defaultMetavar: String = "char"

    override def read(string: String): ValidatedNel[String, Char] = {
      if (string.size == 1) Validated.validNel(string(0))
      else Validated.invalidNel(s"Invalid character: $string")
    }
  }

  implicit val readURI: Argument[URI] = new Argument[URI] {

    override def read(string: String): ValidatedNel[String, URI] =
      try {
        Validated.valid(new URI(string))
      } catch {
        case use: URISyntaxException =>
          Validated.invalidNel(s"Invalid URI: $string (${use.getReason})")
      }

    override def defaultMetavar: String = "uri"
  }

  implicit val readUUID: Argument[UUID] = new Argument[UUID] {

    override def read(string: String): ValidatedNel[String, UUID] =
      try {
        Validated.valid(UUID.fromString(string))
      } catch { case _: IllegalArgumentException => Validated.invalidNel(s"Invalid UUID: $string") }

    override def defaultMetavar: String = "uuid"

  }

  /**
   * prefer reading the right and fallback to left
   */
  implicit def readEither[A, B](implicit A: Argument[A], B: Argument[B]): Argument[Either[A, B]] =
    Argument.declineArgumentSemigroupK.combineK(B.map(Right(_)), A.map(Left(_)))
}
