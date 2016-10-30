package com.monovore.decline

import cats.syntax.all._

import org.scalatest.{Matchers, WordSpec}

class HelpSpec extends WordSpec with Matchers {

  "Help rendering" should {

    "behave correctly" in {
      val parser = Command(
        name = "program",
        header = "A header.",
        options = {
          val first = Opts.flag("first", short = "F", help = "First option.")
          val second = Opts.required[Long]("second", help = "Second option.")
          (first |@| second).map { (first, second) => () }
        }
      )

      Help.render(parser) should equal(
        """Usage: program [--first] [--second <integer>]
          |
          |A header.
          |
          |    --first, -F
          |            First option.
          |    --second <integer>
          |            Second option.
          |""".stripMargin)
    }
  }
}
