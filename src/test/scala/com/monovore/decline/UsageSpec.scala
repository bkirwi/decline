package com.monovore.decline

import cats.data.NonEmptyList
import org.scalatest.{Matchers, WordSpec}

class UsageSpec extends WordSpec with Matchers {

  "Usage" should {

    "handle no opts" in {
      Usage.fromOpts(Opts.apply(15)) should equal(NonEmptyList.of(Usage()))
    }

    "handle a single argument" in {
      val usage = Usage.fromOpts(Opts.option[Int]("foo", "..."))
      println(usage)
    }
  }
}