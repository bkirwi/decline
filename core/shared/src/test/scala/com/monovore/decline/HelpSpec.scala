package com.monovore.decline

import cats.MonoidK
import cats.syntax.all._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HelpSpec extends AnyWordSpec with Matchers {

  "Help rendering" should {

    "behave correctly" in {
      val parser = Command(
        name = "program",
        header = "A header.",
        helpFlag = false
      ) {
        val first = Opts.flag("first", short = "F", help = "First option.").orFalse
        val second = Opts.option[Long]("second", help = "Second option.").orNone
        val third = Opts.option[Long]("third", help = "Third option.") orElse
          Opts.env[Long]("THIRD", help = "Third option env.")
        val flagOpt = Opts
          .flagOption[String](
            "flagOpt",
            help = "Flag option - can either be a flag or an option-argument.",
            metavar = "string",
            short = "l"
          )
          .orNone

        val flagOpts = Opts.flagOptions[String](
          "flag",
          help = "...",
          metavar = "string",
          short = "Ff"
        )
        val subcommands =
          Opts.subcommand("run", "Run a task?") {
            Opts.argument[String]("task")
          }

        (first, second, third, flagOpt, flagOpts, subcommands).tupled
      }

      Help.fromCommand(parser).toString should equal(
        """Usage: program [--first] [--second <integer>] [--third <integer>] [--flagOpt[=<string>]] --flag[=<string>] [--flag[=<string>]]... run
          |
          |A header.
          |
          |Options and flags:
          |    --first, -F
          |        First option.
          |    --second <integer>
          |        Second option.
          |    --third <integer>
          |        Third option.
          |    --flagOpt[=<string>], -l[<string>]
          |        Flag option - can either be a flag or an option-argument.
          |    --flag[=<string>], -F[<string>], -f[<string>]
          |        ...
          |
          |Environment Variables:
          |    THIRD=<integer>
          |        Third option env.
          |
          |Subcommands:
          |    run
          |        Run a task?""".stripMargin
      )
    }

    "be like an Alternative" should {

      def help[A](opts: Opts[A]): String = {
        val command = Command("test-command", "...")(opts)
        Help.fromCommand(command).toString
      }

      val foo = Opts.option[String]("foo", "...")
      val bar = Opts.argument[String]("bar")
      val baz = Opts.flag("baz", "...")
      val empty = MonoidK[Opts].empty

      "right-absorb" in {
        help(empty) should equal(help((foo, empty).tupled))
      }

      "left-distribute" in {
        help((foo <+> bar).map(identity)) should equal(
          help(foo.map(identity) <+> bar.map(identity))
        )
      }

      "right-distribute" in {
        help(((foo <+> bar), baz).tupled) should equal(
          help((foo, baz).tupled <+> (bar, baz).tupled)
        )
      }
    }

    "de-duplicate environment variable arguments" in {

      def help[A](opts: Opts[A]): String = {
        val command = Command("test-command", "...")(opts)
        Help.fromCommand(command).toString
      }

      val foo = Opts.env[String]("foo", "only one")

      help(foo) shouldBe help((foo, foo).tupled)
    }

    "work with Show instance" in {
      val parser = Command(
        name = "program-test",
        header = "A header",
        helpFlag = false
      ) {
        val first = Opts.flag("first", short = "F", help = "First option.").orFalse
        val second = Opts.option[Long]("second", help = "Second option.").orNone
        val third = Opts.option[Long]("third", help = "Third option.") orElse
          Opts.env[Long]("THIRD", help = "Third option env.")
        val fourth = Opts.options[String]("string-fourth", "Multiple options")
        val subcommands =
          Opts.subcommand("run", "Run a task?") {
            (Opts.argument[String]("task"), fourth).tupled
          }

        (first, second, third, subcommands).tupled
      }

      Help.fromCommand(parser).show should equal(
        """Usage: program-test [--first] [--second <integer>] [--third <integer>] run
          |
          |A header
          |
          |Options and flags:
          |    --first, -F
          |        First option.
          |    --second <integer>
          |        Second option.
          |    --third <integer>
          |        Third option.
          |
          |Environment Variables:
          |    THIRD=<integer>
          |        Third option env.
          |
          |Subcommands:
          |    run
          |        Run a task?""".stripMargin
      )
    }
  }
}
