package com.monovore.clique

import cats.syntax.all._

import org.scalatest.{Matchers, WordSpec}

class HelpSpec extends WordSpec with Matchers {

  "Help rendering" should {

    "behave correctly" in {
      val parser = Command(
        name = "program",
        header = "A header.",
        options = {
          val first = Opts.required[String]("first", "EXAMPLE", help = "First option.")
          val second = Opts.required[String]("second", "EXAMPLE", help = "Second option.")
          (first |@| second).map { (first, second) => () }
        }
      )

      Help.render(parser) should equal(
        """Usage: program --first EXAMPLE --second EXAMPLE
          |
          |A header.
          |    --first=EXAMPLE
          |           First option.
          |    --second=EXAMPLE
          |           Second option.
          |""".stripMargin)
    }
  }
}
