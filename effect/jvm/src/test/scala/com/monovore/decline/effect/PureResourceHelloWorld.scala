package com.monovore.decline.effect

import cats.effect.ExitCode
import cats.effect.IO
import cats.effect.Resource
import com.monovore.decline._

object PureResourceHelloWorld
    extends CommandResourceApp(
      name = "pure-hello",
      header = "Pure Hello World with Decline",
      version = "0.0.1"
    ) {

  def main: Opts[Resource[IO, ExitCode]] = {
    val toGreetOpt = Opts.argument[String]("to-greet")
    toGreetOpt.map { toGreet => Resource.eval(IO.println(s"Hello $toGreet").as(ExitCode.Success)) }
  }

}
