package com.monovore.clique

import cats.syntax.all._

import org.scalatest.{Matchers, WordSpec}

class HelpSpec extends WordSpec with Matchers {

  "Help rendering" should {

    "behave correctly" in {
      val parser = ArgParser(
        name = "program",
        header = "A header.",
        options = {
          val first = Opts.string("first", metavar = "EXAMPLE")
          val second = Opts.string("second", metavar = "EXAMPLE")
          (first |@| second).map { (first, second) => () }
        }
      )

      Help.render(parser) should equal(
        """Usage: program --first EXAMPLE --second EXAMPLE
          |
          |A header.
          |""".stripMargin)
    }
  }
}
