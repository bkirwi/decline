package com.monovore.decline

import com.monovore.decline.discipline.ArgumentSuite
import cats.{Eval, SemigroupK}
import cats.data.Validated
import java.util.UUID

class ArgumentSpec extends ArgumentSuite {

  checkArgument[String]("String")
  checkArgument[Int]("Int")
  checkArgument[Long]("Long")
  checkArgument[Short]("Short")
  checkArgument[BigInt]("BigInt")
  checkArgument[UUID]("UUID")

  def example[A: Argument](str: String, opt: Option[A]) =
    assert(Argument[A].read(str).toOption == opt)

  case class RevString(asString: String)
  object RevString {
    implicit val arg: Argument[RevString] =
      // exercise map
      Argument[String].map { s => RevString(s.reverse) }
  }

  test("test some specific examples") {
    example[Either[String, Int]]("12", Some(Right(12)))
    example[Either[String, Int]]("12a", Some(Left("12a")))

    example[RevString]("abc", Some(RevString("cba")))

    example("abc", None)(Argument[String].mapValidated(_ => Validated.invalidNel("nope")))

    example("abc", Some("cba"))(Argument[String].mapValidated { s => Validated.valid(s.reverse) })

    example("ab", Some("ab"))(
      SemigroupK[Argument].combineK(Argument[String], Argument[RevString].map(_.asString))
    )

    example("ab", Some("ba"))(
      SemigroupK[Argument]
        .combineK(Argument[Int].map(_.toString), Argument[RevString].map(_.asString))
    )

    example("ab", None)(
      SemigroupK[Argument].combineK(
        Argument[Int].map(_.toString),
        Argument[String].mapValidated(_ => Validated.invalidNel("nope"))
      )
    )
  }

  test("test defaultMetaVar on combinators") {
    assert(Argument[String].defaultMetavar == Argument[String].map(_.reverse).defaultMetavar)
    assert(Argument[String].defaultMetavar == Argument[String].mapValidated(_ => Validated.invalidNel("nope")).defaultMetavar)
    assert(SemigroupK[Argument].combineK(Argument[Int].map(_.toString), Argument[String]).defaultMetavar ==
      "integer-or-string")
  }
}
