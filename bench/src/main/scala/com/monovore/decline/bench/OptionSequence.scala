package com.monovore.decline.bench

import com.monovore.decline._
import org.openjdk.jmh.annotations.Benchmark
import cats.implicits._

object OptionSequence {

  val n = 20

  val command = Command("demo", "") {
    (1 to n)
      .map { i =>
        Opts.option[String](s"opt$i", "").withDefault(s"$i")
      }
      .reduceRight { (a, b) => (a, b).mapN(_ + _) }
  }
}

class OptionSequence {

  import OptionSequence._

  @Benchmark
  def run(): Unit = {
    command.parse(Seq())
  }
}
