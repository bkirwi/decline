package com.monovore.decline.effect

import cats.effect.ExitCode

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import cats.effect.unsafe.IORuntime

class CommandIOAppSpec extends AnyFlatSpec with Matchers {

  "CommandIOApp" should "return a success exit code when passing an argument" in {
    runApp("me") shouldBe ExitCode.Success
  }

  it should "return a success exit code when passing a version option" in {
    runApp("--version") shouldBe ExitCode.Success
  }

  it should "return a success exit code when passing a help option" in {
    runApp("--help") shouldBe ExitCode.Success
  }

  it should "return an error exit code when passing no arguments" in {
    runApp() shouldBe ExitCode.Error
  }

  it should "expose command so showHelp can be called" in {
    PureHelloWorld.command.showHelp shouldBe """Usage: pure-hello <to-greet>
   |
   |Pure Hello World with Decline
   |
   |Options and flags:
   |    --help
   |        Display this help text.
   |    --version, -v
   |        Print the version number and exit.""".stripMargin
  }

  private[this] def runApp(args: String*): ExitCode =
    PureHelloWorld.run(args.toList).unsafeRunSync()(IORuntime.global)

}
