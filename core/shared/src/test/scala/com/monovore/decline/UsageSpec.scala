package com.monovore.decline

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import cats.syntax.all._

class UsageSpec extends AnyWordSpec with Matchers {

  "Usage" should {

    def show(opts: Opts[_]) = Usage.fromOpts(opts).flatMap(_.show)

    "handle no opts" in {
      Usage.fromOpts(Opts.apply(15)) should equal(List(Usage()))
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
      assert(show(Opts("ok").withDefault("Hi")) == List())
      assert(show(Opts.env[String]("YIKES", "...").withDefault("Hi")) == List())
    }
  }
}
