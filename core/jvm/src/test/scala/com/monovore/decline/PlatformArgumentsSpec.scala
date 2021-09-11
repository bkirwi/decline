package com.monovore.decline

import cats.data.Validated.{Invalid, Valid}
import org.scalacheck.Gen
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks

import java.nio.file.{Path, Paths}
import java.time.temporal.ChronoUnit

class PlatformArgumentsSpec extends AnyWordSpec with Matchers with ScalaCheckDrivenPropertyChecks {

  "Path arguments" should {

    val pathGen = for {
      first <- Gen.alphaStr
      rest <- Gen.listOf(Gen.alphaStr)
    } yield Paths.get(s"/$first", rest: _*)

    "parse some simple paths" in {
      val Valid(path) = Argument[Path].read("/tmp/data/stuff.out")
      path.isAbsolute should equal(true)

      val Valid(relative) = Argument[Path].read("./some/file.txt")
      relative.isAbsolute should equal(false)
    }

    "parse generated paths" in forAll(pathGen) { path =>
      Argument[Path].read(path.toString) should equal(Valid(path))
    }
  }

  "ChronoUnit arguments" should {
    val chronoUnitGen = Gen.oneOf(ChronoUnit.values().toList)

    "parse valid ChronoUnit's successfully" in forAll(chronoUnitGen) { chronoUnit =>
      Argument[ChronoUnit].read(chronoUnit.name()) should equal(Valid(chronoUnit))
    }

    "parse valid ChronoUnit's successfully in lowercase" in forAll(chronoUnitGen) { chronoUnit =>
      Argument[ChronoUnit].read(chronoUnit.name().toLowerCase) should equal(Valid(chronoUnit))
    }

    "parse half-days as a ChronoUnit value" in {
      Argument[ChronoUnit].read("half-days") should equal(Valid(ChronoUnit.HALF_DAYS))
    }

    "return error for invalid ChronoUnit's" in forAll(
      Gen.alphaStr.filterNot(x => ChronoUnit.values().map(_.name()).contains(x))
    ) { invalidChronoUnit =>
      Argument[ChronoUnit].read(invalidChronoUnit) shouldBe a[Invalid[_]]
    }
  }

}
