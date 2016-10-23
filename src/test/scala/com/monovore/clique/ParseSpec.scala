package com.monovore.clique

import cats.syntax.all._
import cats.data.Validated._
import org.scalatest.{Matchers, WordSpec}

class ParseSpec extends WordSpec with Matchers {

  "Parsing" should {

    val whatever = Opts.required[String]("whatever", "OK", help = "Useful!")
    val ghost = Opts.required[String]("ghost", "BOO", help = "Important!")

    "read a single option" in {
      val opts = whatever
      val Valid(result) = Parse.run(List("--whatever", "man"), opts)
      result should equal("man")
    }

    "read a long option with =" in {
      val opts = whatever
      val Valid(result) = Parse.run(List("--whatever=man"), opts)
      result should equal("man")
    }

    "read a couple options" in {
      val opts = (whatever |@| ghost).tupled
      val Valid(result) = Parse.run(List("--whatever", "man", "--ghost", "dad"), opts)
      result should equal(("man", "dad"))
    }

    "fail on out-of-order options" in {
      val opts = (whatever |@| ghost).tupled
      val Invalid(_) = Parse.run(List("--whatever", "--ghost", "dad"), opts)
    }
  }
}
