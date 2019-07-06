package com.monovore.decline.effect

import cats.effect.ExitCode

import org.scalatest.{Matchers, FlatSpec}

class CommandIOAppSpec extends FlatSpec with Matchers {

  "CommandIOApp" should "return a success exit code when passing an argument" in {
    runApp("me") shouldBe ExitCode.Success
  }

  it should "return a success exit code when passing a version option" in {
    runApp("--version") shouldBe ExitCode.Success
  }

  it should "return an error exit code when passing no arguments" in {
    runApp() shouldBe ExitCode.Error
  }

  private[this] def runApp(args: String*): ExitCode =
    PureHelloWorld.run(args.toList).unsafeRunSync()

}