package com.monovore.decline
package refined

import cats.data.Validated

import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive

import org.scalacheck.Gen
import org.scalatest.{Matchers, FlatSpec}

class RefinedArgumentSpec extends FlatSpec with Matchers {

  type PosInt = Int Refined Positive

  "Argument[PosInt]" should "parse positive integers" in {
    Argument[PosInt].read("1") shouldBe Validated.validNel(1: PosInt)
  }

  it should "not accept zero" in {
    Argument[PosInt].read("0") shouldBe Validated.invalidNel("Predicate failed: (0 > 0).")
  }

  it should "not accept values that not numbers" in {
    Argument[PosInt].read("abc") shouldBe Validated.invalidNel("Invalid integer: abc")
  }

}
