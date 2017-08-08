package com.monovore.example.decline

import java.nio.file.Path

import cats.implicits._
import com.monovore.decline._

object ScoptExample extends CommandApp(
  name = "scopt-example",
  header = "Taken after the example in the scopt readme.",
  version = "3.x",
  main = {

    val foo = Opts.option[Int]("foo", short = "f", help = "Optional int!").withDefault(-1)

    val out = Opts.option[Path]("out", short = "o", help = "Required path!")

    val command = Command("foo-bar", "Test command") {
      foo
    }

    val libMax = {
      val libname = Opts.option[String]("libname", help = "Lib name to limit.")
      val max =
        Opts.option[Int]("max", help = "Limit for --libname option.")
          .validate("Specified max must be positive.") { _ > 0 }

      (libname, max).tupled.orNone
    }

    val jars = Opts.options[Path]("jar", short = "j", help = "Jar to include! More args, more jars.").orEmpty

    // SKIPPED: kwargs

    val verbose = Opts.flag("verbose", "Verbose?").orFalse

    val debug = Opts.flag("debug", "Debug mode: shows in full list, not in usage.", visibility = Visibility.Partial).orFalse

    val files = Opts.arguments[Path]("file").orEmpty

    val update = Opts.subcommand("update", help = "A command! This is the command help text.") {

      val keepalive =
        Opts.flag("not-keepalive", help = "Disable keepalive?")
          .map { _ => false }
          .withDefault(true)

      val xyz = Opts.flag("xyz", help = "Boolean prop?").orFalse
      // SKIPPED: xyz as boolean, not flag

      (keepalive, xyz).tupled
        .validate("Can't both keepalive and xyz!") { case (keepalive, xyz) => !(keepalive && xyz) }
    }

    (foo, out, libMax, jars, verbose, debug, files, update)
      .mapN { (foo, out, libMax, jars, verbose, debug, files, update) =>
        println("foo: " + foo)
        println("out: " + out)
        println("libmax: " + libMax)
        println("jars: " + jars)
        println("verbose: " + verbose)
        println("Debug: " + debug)
        println("files: " + files)
        println("update: " + update)
      }
  }
)
