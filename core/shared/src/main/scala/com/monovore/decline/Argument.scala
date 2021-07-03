package com.monovore.decline

import java.net.{URI, URISyntaxException}
import java.util.UUID

import cats.data.{Validated, ValidatedNel}
import cats.{Defer, Functor, SemigroupK}

import scala.annotation.implicitNotFound
import scala.concurrent.duration.{Duration, FiniteDuration}

/**
 * This typeclass captures the information needed to use this type as an option argument.
 *
 * See the [[http://monovore.com/decline/arguments.html documentation]] for more details.
 */
@implicitNotFound(
  "No Argument instance found for ${A}. For more info, see: http://monovore.com/decline/arguments.html#missing-instances"
)
trait Argument[A] { self =>

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
   * convenience function to create Argument instances
   */
  def from[A](defMeta: String)(fn: String => ValidatedNel[String, A]): Argument[A] =
    new Argument[A] {
      override def read(string: String): ValidatedNel[String, A] = fn(string)

      override def defaultMetavar: String = defMeta
    }

  /**
   * Build an argument from a Map of values
   */
  def fromMap[A](defmeta: String, nameToValue: Map[String, A]): Argument[A] =
    new Argument[A] {
      def defaultMetavar: String = defmeta

      private[this] val message: String =
        nameToValue.size match {
          case 0 => s"There are no valid values for $defmeta."
          case 1 => s"Expected ${nameToValue.head._1}."
          case _ =>
            val keys = nameToValue.keys.toList.sorted.mkString(", ")
            s"Expected one of: $keys."
        }

      def read(string: String): ValidatedNel[String, A] =
        nameToValue.get(string) match {
          case Some(t) => Validated.valid(t)
          case None =>
            Validated.invalidNel(s"Unknown value: $string. $message")
        }
    }

  private final case class DeferArgument[A](thunk: () => Argument[A]) extends Argument[A] {
    private var cache: Argument[A] = null

    lazy val evaluated: Argument[A] = {
      @annotation.tailrec
      def loop(thunk: () => Argument[A], writes: List[DeferArgument[A]]): Argument[A] =
        thunk() match {
          case d @ DeferArgument(thunk) if d.cache eq null =>
            loop(thunk, d :: writes)
          case notDefer =>
            writes.foreach(_.cache = notDefer)
            notDefer
        }

      val c = cache
      if (c eq null) loop(thunk, this :: Nil)
      else c
    }

    def read(string: String) = evaluated.read(string)
    def defaultMetavar = evaluated.defaultMetavar
  }

  implicit val declineArgumentDefer: Defer[Argument] =
    new Defer[Argument] {
      def defer[A](arga: => Argument[A]): Argument[A] =
        DeferArgument(() => arga)
    }

  /**
   * We can't add methods to traits in 2.11 without breaking binary compatibility
   */
  implicit final class ArgumentMethods[A](private val self: Argument[A]) extends AnyVal {
    def map[B](fn: A => B): Argument[B] =
      from(self.defaultMetavar)(self.read(_).map(fn))
  }

  implicit val readString: Argument[String] =
    from("string")(Validated.valid(_))

  private def readNum[A](typeName: String)(parse: String => A): Argument[A] =
    from(typeName) { string =>
      try Validated.valid(parse(string))
      catch {
        case nfe: NumberFormatException => Validated.invalidNel(s"Invalid $typeName: $string")
      }
    }

  implicit val declineArgumentFunctor: Functor[Argument] =
    new Functor[Argument] {
      def map[A, B](arga: Argument[A])(fn: A => B): Argument[B] =
        arga.map(fn)
    }

  implicit val declineArgumentSemigroupK: SemigroupK[Argument] =
    new SemigroupK[Argument] {
      override def combineK[A](x: Argument[A], y: Argument[A]): Argument[A] =
        from[A](s"${x.defaultMetavar} | ${y.defaultMetavar}") { string =>
          val ax = x.read(string)
          if (ax.isValid) ax
          else y.read(string)
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

          override def defaultMetavar: String = s"${x.defaultMetavar} | ${y.value.defaultMetavar}"
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

  implicit val readChar: Argument[Char] =
    from("char") { string =>
      if (string.size == 1) Validated.validNel(string(0))
      else Validated.invalidNel(s"Invalid character: $string")
    }

  implicit val readURI: Argument[URI] =
    from("uri") { string =>
      try {
        Validated.valid(new URI(string))
      } catch {
        case use: URISyntaxException =>
          Validated.invalidNel(s"Invalid URI: $string (${use.getReason})")
      }
    }

  implicit val readUUID: Argument[UUID] =
    from("uuid") { string =>
      try {
        Validated.valid(UUID.fromString(string))
      } catch { case _: IllegalArgumentException => Validated.invalidNel(s"Invalid UUID: $string") }
    }

  implicit val readDuration: Argument[Duration] =
    from("duration") { string =>
      try Validated.Valid(Duration(string))
      catch { case _: NumberFormatException => Validated.invalidNel(s"Invalid Duration: $string") }
    }

  implicit val readFiniteDuration: Argument[FiniteDuration] =
    new Argument[FiniteDuration] {
      override def read(string: String): ValidatedNel[String, FiniteDuration] =
        readDuration.read(string) andThen {
          case fd: FiniteDuration => Validated.Valid(fd)
          case _ => Validated.invalidNel(s"Invalid Duration: $string is not finite")
        }

      override def defaultMetavar: String = readDuration.defaultMetavar
    }

  /**
   * prefer reading the right and fallback to left
   */
  implicit def readEither[A, B](implicit A: Argument[A], B: Argument[B]): Argument[Either[A, B]] =
    Argument.declineArgumentSemigroupK.combineK(B.map(Right(_)), A.map(Left(_)))
}
