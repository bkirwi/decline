package com.monovore.decline

import cats.syntax.all._
import cats.data.Validated._
import org.scalatest.{Matchers, WordSpec}

class ParseSpec extends WordSpec with Matchers {

  "Parsing" should {

    val whatever = Opts.required[String]("whatever", "OK", help = "Useful!")
    val ghost = Opts.required[String]("ghost", "BOO", help = "Important!")
    val positional = Opts.requiredArg[String]("EXPECTED")

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

    "fail on unrecognized options, even with arguments" in {
      val Invalid(_) = whatever.parse(List("--whatever=dude", "--unrecognized"))
    }

    "handle a single positional argument" in {
      val Valid("ok") = positional.parse(List("ok"))
    }

    "handle a combined positional argument" in {
      val Valid(result) = (whatever |@| positional).tupled.parse(List("--whatever", "hello", "ok"))
      result should equal("hello" -> "ok")
    }

    "complain about option in argument position" in {
      val Invalid(_) = (whatever |@| positional).tupled.parse(List("--whatever", "hello", "--ok"))
    }

    "obey a --" in {
      val Valid(result) = (whatever |@| positional).tupled.parse(List("--whatever", "hello", "--", "--ok"))
      result should equal("hello" -> "--ok")
    }
  }
}
