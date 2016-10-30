package com.monovore.decline

import cats.syntax.all._
import cats.data.Validated._
import org.scalatest.{Matchers, WordSpec}

class ParseSpec extends WordSpec with Matchers {

  "Parsing" should {

    val whatever = Opts.required[String]("whatever", help = "Useful!")
    val ghost = Opts.required[String]("ghost", short="g", help = "Important!")
    val positional = Opts.requiredArg[String]("expected")

    "read a single option" in {
      val opts = whatever
      val Valid(result) = Parse.apply(List("--whatever", "man"), opts)
      result should equal("man")
    }

    "read a long option with =" in {
      val opts = whatever
      val Valid(result) = Parse.apply(List("--whatever=man"), opts)
      result should equal("man")
    }

    "read a couple options" in {
      val opts = (whatever |@| ghost).tupled
      val Valid(result) = Parse.apply(List("--whatever", "man", "--ghost", "dad"), opts)
      result should equal(("man", "dad"))
    }

    "fail on misaligned options" in {
      val opts = (whatever |@| ghost).tupled
      val Invalid(_) = Parse.apply(List("--whatever", "--ghost", "dad"), opts)
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

    "handle interspersed arguments and options" in {
      val Valid(result) = (whatever |@| Opts.remainingArgs[String]()).tupled.parse(List("foo", "--whatever", "hello", "bar"))
      result should equal("hello" -> List("foo", "bar"))
    }

    "read a short option" in {
      val Valid(result) = ghost.parse(List("-g", "boo"))
      result should equal("boo")
    }

    "read a few short options" in {
      val force = Opts.flag("follow", short = "f", help = "Tail the file continuously.")
      val count = Opts.optional[Int]("count", short = "n", help = "Number of lines to tail.").withDefault(Int.MaxValue)
      val file = Opts.remainingArgs[String]("file")
      val Valid(result) = (force |@| count |@| file).tupled.parse(List("first", "-fn30", "second"))
      result should equal((true, 30, List("first", "second")))
    }
  }
}
