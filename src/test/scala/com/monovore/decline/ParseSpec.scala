package com.monovore.decline

import cats.data.{NonEmptyList, Validated}
import cats.syntax.all._
import cats.data.Validated._
import org.scalatest.{Matchers, WordSpec}

class ParseSpec extends WordSpec with Matchers {

  implicit class Parser[A](opts: Opts[A]) {
    def parse(args: Seq[String]): Validated[List[String], A] = Parse(args.toList, opts)
  }

  "Parsing" should {

    val whatever = Opts.option[String]("whatever", help = "Useful!")
    val ghost = Opts.option[String]("ghost", short="g", help = "Important!")
    val positional = Opts.argument[String]("expected")

    "read a single option" in {
      val opts = whatever
      val Valid(result) = opts.parse(List("--whatever", "man"))
      result should equal("man")
    }

    "read a long option with =" in {
      val opts = whatever
      val Valid(result) = opts.parse(List("--whatever=man"))
      result should equal("man")
    }

    "read a flag" in {
      val opts = Opts.flag("test", "...")
      opts.parse(List("--test")) should equal(Valid(()))
      val Invalid(_) = opts.parse(List())
    }

    "read a couple options" in {
      val opts = (whatever |@| ghost).tupled
      val Valid(result) = opts.parse(List("--whatever", "man", "--ghost", "dad"))
      result should equal(("man", "dad"))
    }

    "fail on misaligned options" in {
      val opts = (whatever |@| ghost).tupled
      val Invalid(_) = opts.parse(List("--whatever", "--ghost", "dad"))
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
      val Valid(result) = (whatever |@| Opts.arguments[String]()).tupled.parse(List("foo", "--whatever", "hello", "bar"))
      result should equal("hello" -> NonEmptyList.of("foo", "bar"))
    }

    "read a short option" in {
      val Valid(result) = ghost.parse(List("-g", "boo"))
      result should equal("boo")
    }

    "read a few short options" in {
      val force = Opts.flag("follow", short = "f", help = "Tail the file continuously.")
      val count = Opts.option[Int]("count", short = "n", help = "Number of lines to tail.")
      val file = Opts.arguments[String]("file")
      val Valid(result) = (force |@| count |@| file).tupled.parse(List("first", "-fn30", "second"))
      result should equal(((), 30, NonEmptyList.of("first", "second")))
    }

    "handle alternative flags" in {

      val first = Opts.flag("first", help = "1").map { _ => 1 }
      val second = Opts.flag("second", help = "2").map { _ => 2 }

      (first orElse second).parse(List("--first")) should equal(Valid(1))
      (first orElse second).parse(List("--second")) should equal(Valid(2))
      val Invalid(_) = (first orElse second).parse(List("--third"))
    }

    "handle alternative arguments" in {

      val one = Opts.argument[String]("single")
      val two = (Opts.argument[String]("left") |@| Opts.argument[String]("right")).tupled

      (one orElse two).parse(List("foo")) should equal(Valid("foo"))
      (one orElse two).parse(List("foo", "bar")) should equal(Valid("foo" -> "bar"))

      (two orElse one).parse(List("foo")) should equal(Valid("foo"))
      (two orElse one).parse(List("foo", "bar")) should equal(Valid("foo" -> "bar"))
    }

    "handle subcommands" in {
      val run = Opts.subcommand("run", "Run the thing!")(
        Opts.option[Int]("foo", help = "Do the thing!").orNone
      )
      val clear = Opts.subcommand("clear", "Clear the thing!")(
        Opts.option[Int]("bar", help = "Do the thing!").orNone
      )

      val opts = run orElse clear

      opts.parse(List("run", "--foo", "77")) should equal(Valid(Some(77)))
      opts.parse(List("clear", "--bar", "16")) should equal(Valid(Some(16)))
    }

    "passes trailing options to subcommands" in {

      val opt = Opts.option[Int]("flag", "...").orNone

      val cmd = Opts.subcommand("run", "Run the thing!")(opt)

      val Valid(run) = (opt |@| cmd).tupled.parse(List("run", "--flag", "77"))
      run should equal(None -> Some(77))
    }
  }
}
