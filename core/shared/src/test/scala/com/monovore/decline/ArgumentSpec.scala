package com.monovore.decline

import cats.Defer
import java.util.UUID

import com.monovore.decline.discipline.ArgumentSuite

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

}
