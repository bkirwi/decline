package com.monovore.decline.bench

import com.monovore.decline._
import org.openjdk.jmh.annotations.Benchmark
import cats.implicits._

object SandwichedArgs {

  val command = Command("demo", "") {

    val a = Opts.argument[String]("first")
    val b = Opts.arguments[String]("middle")
    val c = Opts.argument[String]("last")

    (a, b, c).tupled
  }

  val args = Seq.fill(1000)("cool")
}

class SandwichedArgs {

  import SandwichedArgs._

  @Benchmark
  def run(): Unit = {
    command.parse(args)
  }
}
