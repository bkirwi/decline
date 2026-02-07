package com.monovore.decline

import cats.data.{Ior, NonEmptyList}
import cats.syntax.all._
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class LenientParseSpec extends AnyWordSpec with Matchers {

  def isUnexpectedOption(msg: String): Boolean = msg.startsWith(Messages.UnexpectedOptionPrefix)
  def isUnexpectedArgument(msg: String): Boolean = msg.startsWith(Messages.UnexpectedArgumentPrefix)
  def isAmbiguous(msg: String): Boolean = msg.startsWith(Messages.AmbiguousOptionFlagPrefix)
  def isMissingValue(msg: String): Boolean = msg.startsWith(Messages.MissingValueForOptionPrefix)
  def isUnexpectedValueForFlag(msg: String): Boolean = msg.startsWith(Messages.UnexpectedValueForFlagPrefix)
  def isMissing(msg: String): Boolean = msg.startsWith("Missing expected")

  implicit class LenientParser[A](opts: Opts[A]) {
    val command: Command[A] = Command("test-spec", header = "Test command!", helpFlag = false)(opts)

    def parseLenient(args: Seq[String], env: Map[String, String] = Map()): Ior[Help, A] =
      command.parseLenient(args, env)

    def parseStrict(args: Seq[String], env: Map[String, String] = Map()): Either[Help, A] =
      command.parse(args, env)
  }

  "Lenient Parsing" should {

    val whatever = Opts.option[String]("whatever", help = "Useful!")
    val ghost = Opts.option[String]("ghost", short = "g", help = "Important!")
    val positional = Opts.argument[String]("expected")
    val flag = Opts.flag("test", help = "...", short = "t")

    "succeed with no warnings when input is valid" in {
      val Ior.Right(result) = whatever.parseLenient(List("--whatever", "man"))
      result should equal("man")
    }

    "read a single option with unexpected trailing option" in {
      whatever.parseLenient(List("--whatever", "man", "--unknown")) match {
        case Ior.Both(help, result) =>
          result should equal("man")
          help.warnings.exists(isUnexpectedOption) shouldBe true
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "read a long option with = and unexpected option with value" in {
      whatever.parseLenient(List("--whatever=man", "--unknown=value")) match {
        case Ior.Both(help, result) =>
          result should equal("man")
          help.warnings.exists(isUnexpectedOption) shouldBe true
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "read a couple options with unexpected short options" in {
      val opts = (whatever, ghost).tupled
      opts.parseLenient(List("--whatever", "man", "-x", "--ghost", "dad")) match {
        case Ior.Both(help, result) =>
          result should equal(("man", "dad"))
          help.warnings.exists(isUnexpectedOption) shouldBe true
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "read options with multiple unexpected short options bundled" in {
      val opts = (whatever, ghost).tupled
      opts.parseLenient(List("--whatever", "man", "-xyz", "--ghost", "dad")) match {
        case Ior.Both(help, result) =>
          result should equal(("man", "dad"))
          help.warnings.count(isUnexpectedOption) should equal(3)
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "handle a positional argument with unexpected trailing argument" in {
      positional.parseLenient(List("ok", "extra")) match {
        case Ior.Both(help, result) =>
          result should equal("ok")
          help.warnings.exists(isUnexpectedArgument) shouldBe true
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "handle combined options and positional with unexpected items" in {
      val opts = (whatever, positional).tupled
      opts.parseLenient(List("--whatever", "hello", "ok", "--extra", "trailing")) match {
        case Ior.Both(help, result) =>
          result should equal("hello" -> "ok")
          help.warnings.exists(isUnexpectedOption) shouldBe true
          help.warnings.exists(isUnexpectedArgument) shouldBe true
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "handle -- with unexpected arguments after it" in {
      val opts = (whatever, positional).tupled
      opts.parseLenient(List("--whatever", "hello", "--", "--ok", "extra")) match {
        case Ior.Both(help, result) =>
          result should equal("hello" -> "--ok")
          help.warnings.exists(isUnexpectedArgument) shouldBe true
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "handle interspersed arguments and options with unexpected items" in {
      val opts = (whatever, Opts.arguments[String]()).tupled
      opts.parseLenient(List("foo", "--whatever", "hello", "--extra", "bar")) match {
        case Ior.Both(help, result) =>
          result should equal("hello" -> NonEmptyList.of("foo", "bar"))
          help.warnings.exists(isUnexpectedOption) shouldBe true
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "read a short option with unexpected trailing option" in {
      ghost.parseLenient(List("-g", "boo", "--unknown")) match {
        case Ior.Both(help, result) =>
          result should equal("boo")
          help.warnings.exists(isUnexpectedOption) shouldBe true
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "read a few short options with unexpected option" in {
      val force = Opts.flag("follow", short = "f", help = "...")
      val count = Opts.option[Int]("count", short = "n", help = "...")
      val file = Opts.arguments[String]("file")
      val opts = (force, count, file).tupled
      opts.parseLenient(List("first", "-fn30", "--extra", "second")) match {
        case Ior.Both(help, result) =>
          result should equal(((), 30, NonEmptyList.of("first", "second")))
          help.warnings.exists(isUnexpectedOption) shouldBe true
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "handle alternative flags with unexpected option" in {
      val first = Opts.flag("first", help = "1").map(_ => 1)
      val second = Opts.flag("second", help = "2").map(_ => 2)
      val opts = first orElse second
      opts.parseLenient(List("--first", "--unknown")) match {
        case Ior.Both(help, result) =>
          result should equal(1)
          help.warnings.exists(isUnexpectedOption) shouldBe true
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "handle alternative arguments with unexpected argument" in {
      val one = Opts.argument[String]("single")
      val two = (Opts.argument[String]("left"), Opts.argument[String]("right")).tupled
      val opts = one orElse two
      opts.parseLenient(List("foo", "bar", "extra")) match {
        case Ior.Both(help, result) =>
          result should equal("foo" -> "bar")
          help.warnings.exists(isUnexpectedArgument) shouldBe true
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "handle subcommands with unexpected option in parent" in {
      val sub = Opts.subcommand("run", "...")(
        Opts.option[Int]("foo", help = "...").orNone
      )
      sub.parseLenient(List("--extra", "run", "--foo", "77")) match {
        case Ior.Both(help, result) =>
          result should equal(Some(77))
          help.warnings.exists(isUnexpectedOption) shouldBe true
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "preserve warning order (chronological)" in {
      val opts = (whatever, ghost).tupled
      opts.parseLenient(List("--first", "--whatever", "x", "--second", "-g", "y", "third")) match {
        case Ior.Both(help, _) =>
          help.warnings.filter(isUnexpectedOption).take(2).forall(isUnexpectedOption) shouldBe true
          isUnexpectedArgument(help.warnings.last) shouldBe true
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "accumulate multiple warnings of different types" in {
      val opts = (whatever, ghost).tupled
      opts.parseLenient(List("--unknown", "--whatever", "x", "-q", "-g", "y", "extra")) match {
        case Ior.Both(help, result) =>
          result should equal(("x", "y"))
          help.warnings.count(isUnexpectedOption) should be >= 2
          help.warnings.count(isUnexpectedArgument) should equal(1)
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "fail with error for missing required options" in {
      val Ior.Left(help) = (whatever, ghost).tupled.parseLenient(List("--whatever", "x"))
      help.errors.exists(isMissing) shouldBe true
    }

    "fail and promote warnings to errors when validation fails" in {
      val validated = whatever.validate("must not be empty")(_.nonEmpty)
      validated.parseLenient(List("--whatever", "", "--extra")) match {
        case Ior.Left(help) =>
          help.errors should contain("must not be empty")
          help.errors.exists(isUnexpectedOption) shouldBe true
        case other => fail(s"Expected Ior.Left, got $other")
      }
    }

    "fail on ambiguous option (flag vs option with same name)" in {
      val ambiguousFlag: Opts[Unit] = Opts.flag("xflag", help = "...", short = "x")
      val ambiguousOption: Opts[String] = Opts.option[String]("xoption", help = "...", short = "x")
      val ambiguousOpts = (ambiguousFlag, ambiguousOption).mapN((_, s) => s)
      val Ior.Left(help) = ambiguousOpts.parseLenient(List("-x", "value"))
      help.errors.exists(isAmbiguous) shouldBe true
    }

    "fail on missing value for option" in {
      val Ior.Left(help) = whatever.parseLenient(List("--whatever"))
      help.errors.exists(isMissingValue) shouldBe true
    }

    "fail on unexpected value for flag" in {
      val Ior.Left(help) = flag.parseLenient(List("--test=value"))
      help.errors.exists(isUnexpectedValueForFlag) shouldBe true
    }

    "propagate lenient mode into subcommands" in {
      val subOpt = Opts.option[Int]("sub-opt", help = "Subcommand option")
      val sub = Opts.subcommand("run", "Run subcommand")(subOpt)
      sub.parseLenient(List("run", "--sub-opt", "42", "--unexpected")) match {
        case Ior.Both(help, result) =>
          result should equal(42)
          help.warnings.exists(isUnexpectedOption) shouldBe true
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "show subcommand Help when subcommand fails" in {
      val subOpt = Opts.option[Int]("sub-opt", help = "Subcommand option")
      val sub = Opts.subcommand("run", "Run subcommand")(subOpt)
      val mainOpt = Opts.option[String]("main-opt", help = "Main option")
      val mainWithSub = (mainOpt, sub).tupled
      val cmd = Command("main", "Main command")(mainWithSub)

      cmd.parseLenient(List("--main-opt", "x", "run")) match {
        case Ior.Left(help) =>
          help.errors.exists(isMissing) shouldBe true
          help.toString should include("--sub-opt")
          help.toString should include("Run subcommand")
        case other => fail(s"Expected Ior.Left, got $other")
      }
    }

    "show main Help when main command fails (even if subcommand also has error)" in {
      val subOpt: Opts[Int] = Opts.option[Int]("sub-opt", help = "Subcommand option")
      val sub: Opts[Int] = Opts.subcommand("run", "Run subcommand")(subOpt)
      val mainOpt: Opts[String] = Opts.option[String]("main-opt", help = "Main option")
      val mainWithSub: Opts[(String, Int)] = (mainOpt, sub).tupled
      val cmd = Command("main", "Main command")(mainWithSub)

      // Missing --main-opt AND missing --sub-opt in subcommand
      cmd.parseLenient(List("main-opt a extra-main-opt run sub-opt --extra-sub-opt")) match {
        case Ior.Left(help) =>
          println(help)
          help.errors.exists(isMissing) shouldBe true
          // Should show main command's Help since main option is missing
          help.toString should include("--main-opt")
          help.toString should include("Main command")
        case other => fail(s"Expected Ior.Left, got $other")
      }
    }

    "show main Help when only main option fails" in {
      val subOpt: Opts[Int] = Opts.option[Int]("sub-opt", help = "Subcommand option")
      val sub: Opts[Int] = Opts.subcommand("run", "Run subcommand")(subOpt)
      val mainOpt: Opts[String] = Opts.option[String]("main-opt", help = "Main option")
      val mainWithSub: Opts[(String, Int)] = (mainOpt, sub).tupled
      val cmd = Command("main", "Main command")(mainWithSub)

      // Missing --main-opt but subcommand is valid
      cmd.parseLenient(List("run", "--sub-opt", "42")) match {
        case Ior.Left(help) =>
          help.errors.exists(isMissing) shouldBe true
          // Should show main command's Help since main option is missing
          help.toString should include("--main-opt")
          help.toString should include("Main command")
        case other => fail(s"Expected Ior.Left, got $other")
      }
    }

    "accumulate warnings from both parent and subcommand in lenient mode" in {
      val subOpt = Opts.option[Int]("sub-opt", help = "...")
      val sub = Opts.subcommand("run", "...")(subOpt)
      val mainOpt = Opts.option[String]("main-opt", help = "...")
      val mainWithSub = (mainOpt, sub).tupled

      mainWithSub.parseLenient(
        List("--main-opt", "x", "--parent-unknown", "run", "--sub-opt", "42", "--child-unknown")
      ) match {
        case Ior.Both(help, result) =>
          result should equal(("x", 42))
          help.warnings.count(isUnexpectedOption) should equal(2)
        case other => fail(s"Expected Ior.Both, got $other")
      }
    }

    "fail fast in subcommand strict mode but not accumulate parent warnings" in {
      val subOptStrict: Opts[Int] = Opts.option[Int]("sub-opt", help = "...")
      val subStrict: Opts[Int] = Opts.subcommand("run", "...")(subOptStrict)
      val mainOptStrict: Opts[String] = Opts.option[String]("main-opt", help = "...")
      val mainWithSubStrict: Opts[(String, Int)] = (mainOptStrict, subStrict).tupled

      val Left(help) = mainWithSubStrict.parseStrict(
        List("--main-opt", "x", "run", "--sub-opt", "42", "--unexpected")
      )
      help.errors.count(w => isUnexpectedOption(w)) should equal(1)
    }
  }

  "Strict Parsing" should {

    val whatever = Opts.option[String]("whatever", help = "Useful!")
    val positional = Opts.argument[String]("expected")

    "fail immediately on unexpected option" in {
      val Left(help) = whatever.parseStrict(List("--whatever", "man", "--unknown"))
      help.errors.exists(isUnexpectedOption) shouldBe true
    }

    "fail immediately on unexpected argument" in {
      val Left(help) = positional.parseStrict(List("ok", "extra"))
      help.errors.exists(isUnexpectedArgument) shouldBe true
    }

    "not accumulate multiple unexpected items" in {
      val Left(help) = whatever.parseStrict(List("--whatever", "man", "--first", "--second"))
      help.errors.count(isUnexpectedOption) shouldBe 1
    }

    "fail on unrecognized options, even with valid arguments" in {
      val Left(_) = whatever.parseStrict(List("--whatever=dude", "--unrecognized"))
    }
  }

}






