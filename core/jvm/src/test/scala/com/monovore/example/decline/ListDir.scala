package com.monovore.example.decline

import cats.implicits._
import com.monovore.decline._

object ListDir extends CommandApp(
  name = "ls",
  header = "List information about files.",
  version = "1.0",
  main = {

    val color =
      Opts.option[String]("color", metavar = "when", help = "Colorize the output: 'always', 'auto', or 'never'")
        .withDefault("always")

    val all =
      Opts.flag("all", short = "a", help = "Do not ignore hidden files.")
        .orFalse

    val directory = Opts.arguments[String]("directory").orEmpty

    (color, all, directory).mapN { (color, all, directory) =>

      println("Color: "  + color)
      println("All: "  + all)
      println("Dir: " + directory)
    }
  }
)
