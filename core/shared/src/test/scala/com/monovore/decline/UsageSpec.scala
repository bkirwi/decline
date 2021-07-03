package com.monovore.decline

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class UsageSpec extends AnyWordSpec with Matchers {

  "Usage" should {

    "handle no opts" in {
      Usage.fromOpts(Opts.apply(15)) should equal(List(Usage()))
    }

    "handle a single argument" in {
      val usage = Usage.fromOpts(Opts.option[Int]("foo", "...")).flatMap { _.show }
      usage should equal(List("--foot <integer>"))
    }

    "show environment variables combined with options as optional" in {
      val usage = Usage.fromOpts(
        Opts.option[Int]("whatever", "...") orElse Opts.env[Int]("WHATEVER", "...")).flatMap { _.show }
      usage should equal(List("[--whatever <integer>]"))
    }
  }
}
