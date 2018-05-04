package com.monovore.decline

import org.scalatest.{Matchers, WordSpec}

class Foo(x: Int)

class Bar(x: Int)

object Bar {
  implicit val barMetavar = Metavar.instance[Bar]("barrr")
}

class MetavarSpec extends WordSpec with Matchers {

  "Metavar" should {
    "use 'integer' for integer types" in {
      Metavar[Int].name shouldBe "integer"
      Metavar[Long].name shouldBe "integer"
      Metavar[BigInt].name shouldBe "integer"
    }

    "generate a reasonable Metavar[Foo]" in {
      Metavar[Foo].name shouldBe "foo"
    }

    "prefer local instances" in {
      implicit val m = Metavar.instance[Foo]("foooo")
      Metavar[Foo].name shouldBe "foooo"
    }

    "prefer instances from the companion" in {
      Metavar[Bar].name shouldBe "barrr"
    }
  }
  
}
