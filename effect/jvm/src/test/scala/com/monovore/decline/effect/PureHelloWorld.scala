package com.monovore.decline.effect

import cats.effect.IO
import com.monovore.decline._

object PureHelloWorld
    extends CommandIOApp[IO](
      name = "pure-hello",
      header = "Pure Hello World with Decline",
      version = "0.0.1"
    ) {

  def main: Opts[IO[Unit]] = {
    val toGreetOpt = Opts.argument[String]("to-greet")
    toGreetOpt.map { toGreet =>
      IO(println(s"Hello $toGreet"))
    }
  }

}
