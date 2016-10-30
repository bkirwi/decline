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
          val subcommands = Opts.subcommands(
            Opts.command("run", "Run a task?") {
              Opts.requiredArg[String]("task")
            }
          )
          (first |@| second |@| subcommands).tupled
        }
      )

      Help.render(parser) should equal(
        """Usage: program [--first] [--second <integer>] <command> [<args>]
          |
          |A header.
          |
          |Options and flags:
          |    --first, -F
          |            First option.
          |    --second <integer>
          |            Second option.
          |
          |Subcommands: run
          |
          |Usage: run <task>
          |
          |Run a task?""".stripMargin)
    }
  }
}
