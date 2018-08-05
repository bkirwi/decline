package com.monovore.decline

import org.scalatest.{Matchers, WordSpec}

class UsageSpec extends WordSpec with Matchers {

  "Usage" should {

    "handle no opts" in {
      Usage.fromOpts(Opts.apply(15)) should equal(List(Usage()))
    }

    "handle a single argument" in {
      val usage = Usage.fromOpts(Opts.option[Int]("foo", "...")).flatMap { _.show }
      usage should equal(List("--foo <integer>"))
    }
  }
}
