package com.monovore.example.decline

import cats.syntax.all._
import com.monovore.decline._
import Command.defaultParserConfig

object Git
    extends CommandApp(
      name = "git",
      header = "Test app for git-style subcommands.",
      main = {

        val status = Opts.subcommand("status", "Print status!") {
          Opts { println("STATUS") }
        }

        val commit = Opts.subcommand("commit", "Commit!") {

          val all = Opts.flag("all", short = "a", help = "All files.").orFalse

          val message = Opts.option[String]("message", short = "m", help = "Commit message").orNone

          (all, message).tupled
            .map { tuple => println("COMMIT " + tuple) }
        }

        val help =
          Opts.subcommand("help", "Print the main help text and exit.")(Opts.unit).asHelp

        status orElse commit orElse help
      }
    )
