package com.monovore.decline
package refined

import cats.{Eq, Show}
import cats.data.Validated

import com.monovore.decline.discipline.ArgumentSuite

import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric._
import eu.timepit.refined.scalacheck.numeric._

class RefinedArgumentSpec extends ArgumentSuite {

  type PosInt = Int Refined Positive

  implicit val eqPosInt: Eq[PosInt] = Eq.fromUniversalEquals
  implicit val showPosInt: Show[PosInt] = Show.show(_.value.toString)

  checkArgument[PosInt]("Int Refined Positive")

  test("should not accept zero") {
    Argument[PosInt].read("0") shouldBe Validated.invalidNel("Predicate failed: (0 > 0).")
  }

  test("should not accept values that not numbers") {
    Argument[PosInt].read("abc") shouldBe Validated.invalidNel("Invalid integer: abc")
  }

}
