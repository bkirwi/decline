package com.monovore.decline

import com.monovore.decline.discipline.ArgumentSuite
import cats.{Defer, SemigroupK, Show}
import cats.data.Validated
import org.scalacheck.{Arbitrary, Gen}

import java.util.UUID
import scala.concurrent.duration.{Duration, FiniteDuration}

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

  // The default Scalacheck Gen.duration generator also includes Duration.Undefined hence why we create our own. Note
  // that Duration.Undefined only occurs when someone does invalid math operations on an valid Duration instance which
  // is a case that can never occur in the context of command line argument passing.
  val validArbitraryDuration = Arbitrary(
    Gen.frequency(
      1 -> Gen.const(Duration.Inf),
      1 -> Gen.const(Duration.MinusInf),
      1 -> Gen.const(Duration.Zero),
      6 -> Gen.finiteDuration
    )
  )

  // The default Cats Show instance of Duration that uses .toString internally doesn't create valid String
  // representations of Infinity that can be parsed by Duration.apply(s: String)
  val validShowDuration = Show.show[Duration] {
    case Duration.MinusInf => "MinusInf"
    case Duration.Inf => "PlusInf"
    case duration: Duration => duration.toString
  }

  checkArgument[Duration](name = "Duration")(
    implicitly,
    validArbitraryDuration,
    validShowDuration,
    implicitly
  )

  // Explicitly test other cases of infinity which Duration.apply(s: String) also accepts
  val durationReader = implicitly[Argument[Duration]]

  test("Duration argument can correctly parse Inf") {
    durationReader.read("Inf").toOption should contain(Duration.Inf)
  }

  test("Duration argument can correctly parse +Inf") {
    durationReader.read("+Inf").toOption should contain(Duration.Inf)
  }

  test("Duration argument can correctly parse -Inf") {
    durationReader.read("-Inf").toOption should contain(Duration.MinusInf)
  }

  checkArgument[FiniteDuration](name = "FiniteDuration")

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
