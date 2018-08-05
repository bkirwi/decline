package com.monovore.decline

import cats.Eq
import cats.data.Validated._
import cats.data.{NonEmptyList, Validated}
import cats.implicits._
import cats.laws.discipline.AlternativeTests
import org.scalacheck.{Arbitrary, Gen, Prop}
import org.scalactic.anyvals.PosInt
import org.scalatest.prop.Checkers
import org.scalatest.{Matchers, WordSpec}

class ParseSpec extends WordSpec with Matchers with Checkers {

  object gen {
    val longNames = Gen.oneOf("foo", "bar", "baz-quux")
    val shortNames = Gen.oneOf("a", "Z", "?", "b")
    val ints = Gen.choose(0, 1000)

    lazy val intOpts: Gen[Opts[Int]] = Gen.oneOf[Opts[Int]](
      for {
        name <- longNames
      } yield Opts.option[Int](name, "..."),
      for {
        name <- longNames
      } yield Opts.options[String](name, "...").map { _.toList.size },
      Opts.argument[Int](),
      for {
        name <- longNames
        value <- ints
      } yield Opts.flag(name, "...").map { _ => value },
      for {
        opts <- Gen.delay { intOpts }
      } yield opts.map { _ => () }.asHelp,
      for {
        opts <- Gen.delay { intOpts }
      } yield opts.map { _ + 100 },
      for {
        one <- Gen.delay { intOpts }
        other <- Gen.delay { intOpts }
      } yield one orElse other,
      for {
        one <- Gen.delay { intOpts }
        other <- Gen.delay { intOpts }
      } yield (one, other).mapN { _ + _ },
      for {
        value <- ints
      } yield Opts { value },
      Opts.arguments[String]().map { _.toList.size },
      Opts.never,
      for {
        name <- longNames
        opts <- Gen.delay { intOpts }
      } yield Opts.subcommand(name, "...")(opts)
    )

    val funOpts = for {
      opts <- intOpts
    } yield opts.map { a => { b: Int => a + b } }

    val intArgs = Gen.choose(0, 1000).map { _.toString }

    val options = Gen.oneOf(
      longNames.map { long => s"--$long" },
      shortNames.map { short => s"-$short" }
    )

    val tests =
      Gen.listOf(
        Gen.oneOf(
          longNames.map { name => s"--$name" },
          shortNames.map { name => s"-$name" },
          ints.map { _.toString },
          Gen.alphaStr
        )
      )
  }



  implicit class Parser[A](opts: Opts[A]) {
    val command = Command("parse-spec", header = "Test command!", helpFlag = false)(opts)
    def parse(args: Seq[String], env: Map[String, String] = Map()): Validated[List[String], A] = {
      Validated.fromEither(command.parse(args, env).left.map { _.errors })
    }
  }

  "Parsing" should {

    "meet the alternative laws" in {

      implicit val ints: Arbitrary[Opts[Int]] = Arbitrary(gen.intOpts)

      implicit val functions: Arbitrary[Opts[Int => Int]] = Arbitrary(gen.funOpts)

      val passesAlternativeLaws = Prop.forAll(gen.tests) { input =>

        // This is a bit interesting: two Opts instances are equal if they return
        // the same values for the same input. We test this by generating many
        // random inputs above, and then testing with equality over that input.
        implicit def equalOnInput[A : Eq] = new Eq[Opts[A]] {
          override def eqv(x: Opts[A], y: Opts[A]): Boolean = {
            def run(opts: Opts[A]) = opts.parse(input).toOption
            Eq[Option[A]].eqv(run(x), run(y))
          }
        }

        AlternativeTests[Opts].alternative[Int, Int, Int].all.properties
          .map { case (label, prop) => prop.label(label) }
          .reduce { _ ++ _ }
      }

      check(passesAlternativeLaws, MinSuccessful(PosInt(100)))
    }

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

    "work as expected with numeric options" in {
      def opt[A: Argument]: Opts[A] = Opts.option[A]("num", help = "some number")
      def good[A: Argument](a: A) = {
        val str = a.toString
        val Valid(result) = opt[A].parse(List("--num", str))
        result should equal(a)
      }
      def bad[A: Argument](str: String) = {
        val aopt = opt[A]
        aopt.parse(List("--num", str)) match {
          case Valid(s) => fail(s"expected to fail to parse $str, got $s")
          case Invalid(_) => succeed
        }
      }

      good[Int](42)
      good[Long](-1000L)
      good[Float](12.21f)
      good[Float](-1e3f)
      good[Double](42.424242)
      good[BigInt](BigInt(100))
      good[BigDecimal](BigDecimal(1.1))

      bad[Int]("-1e0")
      bad[Int]("1.2")
      bad[Long]("11111111111111111111111111111111111111111111111111111111111111")
      bad[Float]("-e")
      bad[Double]("--foo")
      bad[BigInt]("1.23")
      bad[BigDecimal]("pi")
    }

    "read a flag" in {
      val opts = Opts.flag("test", "...")
      opts.parse(List("--test")) should equal(Valid(()))
      val Invalid(_) = opts.parse(List())
    }

    "read a couple options" in {
      val opts = (whatever, ghost).tupled
      val Valid(result) = opts.parse(List("--whatever", "man", "--ghost", "dad"))
      result should equal(("man", "dad"))
    }

    "fail on misaligned options" in {
      val opts = (whatever, ghost).tupled
      val Invalid(_) = opts.parse(List("--whatever", "--ghost", "dad"))
    }

    "fail on unrecognized options, even with arguments" in {
      val Invalid(_) = whatever.parse(List("--whatever=dude", "--unrecognized"))
    }

    "handle a single positional argument" in {
      val Valid("ok") = positional.parse(List("ok"))
    }

    "handle a combined positional argument" in {
      val Valid(result) = (whatever, positional).tupled.parse(List("--whatever", "hello", "ok"))
      result should equal("hello" -> "ok")
    }

    "complain about option in argument position" in {
      val Invalid(_) = (whatever, positional).tupled.parse(List("--whatever", "hello", "--ok"))
    }

    "obey a --" in {
      val Valid(result) = (whatever, positional).tupled.parse(List("--whatever", "hello", "--", "--ok"))
      result should equal("hello" -> "--ok")
    }

    "handle interspersed arguments and options" in {
      val Valid(result) = (whatever, Opts.arguments[String]()).tupled.parse(List("foo", "--whatever", "hello", "bar"))
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
      val Valid(result) = (force, count, file).tupled.parse(List("first", "-fn30", "second"))
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
      val two = (Opts.argument[String]("left"), Opts.argument[String]("right")).tupled

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
      val Valid(run) = (opt, cmd).tupled.parse(List("run", "--flag", "77"))
      run should equal(None -> Some(77))
    }

    "distribute over pathological options" in {
      val a = Opts.unit
      val b = Opts.flag("test", "...")
      val c = b
      val first = ((a orElse b), c).tupled
      val second = (a, c).tupled orElse (b, c).tupled
      val input = List("--test")
      first.parse(input).toOption should equal(second.parse(input).toOption)
    }

    "right-distribute for weird args" in {
      val a = Opts(1)
      val b = Opts.argument[Int]("b")
      val c = Opts.argument[Int]("c") orElse Opts(3)
      val first = ((a orElse b), c).tupled
      val second = (a, c).tupled orElse (b, c).tupled
      val input = List("908")
      first.parse(input).toOption should equal(second.parse(input).toOption)
    }

    "right-distribute for weird args, take two" in {
      val a = Opts.argument[Int]("a")
      val b = Opts(2)
      val c = Opts.argument[Int]("c") orElse Opts(3)
      val first = ((a orElse b), c).tupled
      val second = (a, c).tupled orElse (b, c).tupled
      val input = List("908")
      first.parse(input).toOption should equal(second.parse(input).toOption)
    }

    "right-distribute for options and flags, take two" in {
      val a = Opts.flag("foo", "...")
      val b = Opts.option[String]("bar", "...")
      val c = Opts.flag("foo", "...")
      val first = ((a orElse b), c).tupled
      val second = (a, c).tupled orElse (b, c).tupled
      val input = List("--foo", "--bar", "-b")
      first.parse(input) should equal(second.parse(input))
    }

    "right-distribute for conflicting positional args and flags" in {
      val a = Opts.flag("test", "...") orElse Opts.argument[String]("a")
      val b = Opts("ok")
      val c = Opts.argument[String]("c").orNone
      val first = ((a orElse b), c).tupled
      val second = (a, c).tupled orElse (b, c).tupled
      val input = List("one")
      first.parse(input) should equal(second.parse(input))
    }

    "associate!" in {
      val a = Opts.flag("bar", "...")
      val b = Opts.arguments[String]("b")
      val c = Opts.argument[String]("c")
      val first = ((a, b).tupled, c).tupled.map { case ((a, b), c) => (a, b, c) }
      val second = (a, (b, c).tupled).tupled.map { case (a, (b, c)) => (a, b, c) }
      val input = List("one", "two", "--bar", "three")
      first.parse(input) should equal(second.parse(input))
    }

    "handle large argument lists" in {
      for (max <- List(3, 10, 100000)) {
        val opts = (Opts.argument[Int](), Opts.arguments[Int](), Opts.argument[Int]()).tupled
        opts.parse((1 to max).map(_.toString)) should equal(Valid((1, NonEmptyList(2, (3 until max).toList), max)))
      }
    }

    "read from the environment" when {
      "the variable is present" should {
        "read the variable" in {
          val opts = whatever orElse Opts.envVar[Int]("WHATEVER")
          val env = Map("WHATEVER" -> "123")
          val Valid(result) = opts.parse(List(), env=env)
          result should equal(123)
        }
      }

      "the variable is not valid" should {
        "display a suitable error" in {
          val opts = Opts.envVar[Int]("WHATEVER")
          val env = Map("WHATEVER" -> "someint")
          val Invalid(errs) = opts.parse(List(), Map("WHATEVER" -> "invalidint"))
          errs should equal(List("Error reading WHATEVER from environment: Invalid integer: invalidint"))
        }
      }
    }
  }
}
