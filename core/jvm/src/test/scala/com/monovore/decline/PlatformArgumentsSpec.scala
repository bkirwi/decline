package com.monovore.decline

import java.nio.file.{Path, Paths}

import cats.data.Validated.Valid
import org.scalacheck.Gen
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.prop.GeneratorDrivenPropertyChecks

class PlatformArgumentsSpec extends WordSpec with Matchers with GeneratorDrivenPropertyChecks {

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

}
