package com.monovore.decline

import java.nio.file.{Path, Paths}

import cats.data.Validated._
import org.scalacheck.Gen
import org.scalatest.prop.GeneratorDrivenPropertyChecks
import org.scalatest.{Matchers, PropSpec, WordSpec}

class ArgumentSpec extends WordSpec with Matchers with GeneratorDrivenPropertyChecks {

  "String arguments" should {

    "pass through unchanged" in forAll { str: String =>
      Argument[String].read(str) should equal(Valid(str))
    }
  }

  "Integer arguments" should {

    "parse normal integers" in forAll { int: Int =>
      Argument[Int].read(int.toString) should equal(Valid(int))
    }

    "parse long integers" in forAll { int: Long =>
      Argument[Long].read(int.toString) should equal(Valid(int))
    }

    "parse big integers" in forAll { int: BigInt =>
      Argument[BigInt].read(int.toString) should equal(Valid(int))
    }
  }
}
