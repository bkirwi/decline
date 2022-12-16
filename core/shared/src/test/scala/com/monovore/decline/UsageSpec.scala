package com.monovore.decline

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cats.syntax.all._

class UsageSpec extends AnyWordSpec with Matchers {

  "Usage" should {

    def show(opts: Opts[_]) = Usage.fromOpts(opts).flatMap(_.show)

    "handle no opts" in {
      show(Opts.apply(15)) should equal(List(""))
    }

    "handle a single argument" in {
      val usage = show(Opts.option[Int]("foo", "..."))
      usage should equal(List("--foo <integer>"))
    }

    "show environment variables combined with options as optional" in {
      val usage = show(Opts.option[Int]("whatever", "...") orElse Opts.env[Int]("WHATEVER", "..."))
      usage should equal(List("[--whatever <integer>]"))
    }

    "display optional option-arguments correctly" in {
      val usage =
        show(Opts.flagOption[String]("flagOpt", "..."))

      usage should equal(
        List(
          "--flagOpt[=<string>]"
        )
      )
    }

    "discard empty alternatives" in {
      assert(show(Opts("ok").withDefault("Hi")) == List(""))
      assert(
        show(
          (
            Opts.option[Int]("number", "Print N times", metavar = "N"),
            Opts.env[String]("GREETING", "A greeting").withDefault("Hi"), // FIRST []
            Opts.env[String]("BODY", "The content"), // NO DEFAULT - OK
            Opts.env[String]("FOOTER", "Ending").withDefault("Best regards"), // SECOND []
            Opts.option[String]("name", "Who to greet")
          ).tupled
        ) == List("--number <N> --name <string>")
      )
    }
  }
}
