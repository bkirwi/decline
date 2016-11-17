package com.monovore.example.decline

import cats.implicits._
import com.monovore.decline._

object ListDir extends CommandApp(
  name = "ls",
  header = "List information about files.",
  main = {

    val color =
      Opts.option[String]("color", metavar = "when", help = "Colorize the output: 'always', 'auto', or 'never'")
        .withDefault("always")

    val humanReadable = Opts.flag("human-readable", short = "h", help = "Print human readable sizes.")

    val directory = Opts.arguments[String]("directory")

    (color |@| humanReadable |@| directory).map { (color, humanReadable, directory) =>

      println("Color: "  + color)
      println("Human readable: "  + humanReadable)
      println("Dir: " + directory)
    }
  }
)
