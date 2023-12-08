package com.monovore.decline.effect

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class CommandResourceAppSpec extends AnyFlatSpec with Matchers {

  "CommandResourceApp" should "return a success exit code when passing an argument" in {
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

  private[this] def runApp(args: String*): ExitCode =
    PureResourceHelloWorld.run(args.toList).use(IO.pure).unsafeRunSync()(IORuntime.global)

}
