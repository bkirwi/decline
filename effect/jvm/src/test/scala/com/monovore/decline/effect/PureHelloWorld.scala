package com.monovore.decline.effect

import cats.effect.{ExitCode, IO}
import cats.implicits._

import com.monovore.decline._

object PureHelloWorld extends CommandIOApp(
  name = "pure-hello",
  header = "Pure Hello World with Decline",
  version = "0.0.1"
) {

  def main: Opts[IO[ExitCode]] = {
    val toGreetOpt = Opts.argument[String]("to-greet")
    toGreetOpt.map { toGreet =>
      IO(println(s"Hello $toGreet")).as(ExitCode.Success)
    }
  }
  
}