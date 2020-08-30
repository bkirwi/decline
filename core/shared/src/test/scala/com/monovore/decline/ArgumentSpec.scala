package com.monovore.decline

import com.monovore.decline.discipline.ArgumentSuite
import cats.{Defer, Eval, SemigroupK}
import cats.data.Validated
import java.util.UUID

class ArgumentSpec extends ArgumentSuite {

  test("check defer") {
    var cnt = 0
    val ai = Defer[Argument].defer {
      cnt += 1
      Argument[Int]
    }
    assert(cnt == 0)

    val ai2 = Defer[Argument].defer(ai)

    // ai is evaluated first
    assert(ai.read("42").toOption == Some(42))
    assert(cnt == 1)
    assert(ai.read("314").toOption == Some(314))
    assert(cnt == 1)

    assert(ai.defaultMetavar == Argument[Int].defaultMetavar)

    // now test a2 which is a defer of a defer
    assert(ai2.read("271").toOption == Some(271))
    assert(cnt == 1)
  }

  test("check defer (nesting)") {
    var cnt = 0
    val ai = Defer[Argument].defer {
      cnt += 1
      Argument[Int]
    }
    assert(cnt == 0)

    val ai2 = Defer[Argument].defer(ai)

    // first test a2 which is a defer of a defer
    assert(ai2.read("271").toOption == Some(271))

    assert(cnt == 1)
    // ai is evaluated second, ai2 should have triggered it
    assert(ai.read("42").toOption == Some(42))
    assert(cnt == 1)
    assert(ai.read("314").toOption == Some(314))
    assert(cnt == 1)

    assert(ai.defaultMetavar == Argument[Int].defaultMetavar)
  }

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
        Argument.from[String]("string")(_ => Validated.invalidNel("nope"))
      )
    )
  }

  test("test defaultMetaVar on combinators") {
    assert(Argument[String].defaultMetavar == Argument[String].map(_.reverse).defaultMetavar)
    assert(SemigroupK[Argument].combineK(Argument[Int].map(_.toString), Argument[String]).defaultMetavar ==
      "integer | string")
  }


  test("test Argument.fromMap") {
    val a0 = Argument.fromMap("foo", Map.empty)
    val r0 = a0.read("bar").toEither.left.toOption
    assert(r0.isDefined)
    r0.foreach { s => assert(s.head.contains("no valid values")) }

    val a1 = Argument.fromMap("foo", Map("1" -> 1))
    val r1 = a1.read("bar").toEither.left.toOption
    assert(r1.isDefined)
    r1.foreach { s => assert(s.head.contains("Expected 1")) }

    val r1b = a1.read("1").toEither.right.toOption
    assert(r1b == Some(1))

    val a2 = Argument.fromMap("foo", Map("1" -> 1, "2" -> 2))
    val r2 = a2.read("bar").toEither.left.toOption
    assert(r2.isDefined)
    r2.foreach { s => assert(s.head.contains("Expected one of: 1, 2")) }

    val r2b = a2.read("2").toEither.right.toOption
    assert(r2b == Some(2))
    val r2c = a2.read("1").toEither.right.toOption
    assert(r2c == Some(1))
  }
}
