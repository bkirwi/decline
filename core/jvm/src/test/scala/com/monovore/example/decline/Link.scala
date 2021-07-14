package com.monovore.example.decline

import java.nio.file.Path

import cats.syntax.all._
import com.monovore.decline.{CommandApp, Opts}

object Link
    extends CommandApp(
      name = "ln",
      header = "Create links. (Used for testing complex alternative usages.)",
      main = {

        val target = Opts.argument[Path]("target")
        val linkName = Opts.argument[Path]("link name")
        val targets = Opts.arguments[Path]("target")
        val directory = Opts.argument[Path]("directory")

        val first = {
          val nonDirectory = Opts.flag("no-target-directory", short = "T", help = "...").orFalse

          (nonDirectory, target, linkName).mapN { (_, target, link) =>
            println(s"Create a link to $target with name $link.")
          }
        }

        val second = target.map { (target) =>
          println(s"Create a link to $target in the current directory.")
        }

        val third =
          (targets, directory).mapN { (targets, dir) =>
            println(s"Create links in $dir to: ${targets.toList.mkString(", ")}")
          }

        val fourth = {
          val isDirectory = Opts.option[Path]("target-directory", short = "t", help = "...")

          (isDirectory, targets).mapN { (dir, targets) =>
            println(s"Create links in $dir to: ${targets.toList.mkString(", ")}")
          }
        }

        first <+> second <+> third <+> fourth
      }
    )
