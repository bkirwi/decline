package com.monovore.decline

import RenderUtils._

private[decline] case class HelpArgs(
    errors: List[String],
    optionHelp: List[OptHelp],
    commandsHelp: List[CommandHelp],
    envHelp: List[EnvOptionHelp],
    usages: List[Usage],
    description: String
)

private[decline] case class OptHelp(variants: List[(String, Option[String])], help: String) {
  def show(theme: Theme): List[String] = {
    val newValue = Theme.ArgumentRenderingLocation.InOptions

    val argLine = variants
      .map { case (name, metavarOpt) =>
        theme.optionName(name, newValue) + metavarOpt
          .map { metavar =>
            val spaces = metavar.takeWhile(_.isWhitespace).length
            (" " * spaces) + theme.metavar(metavar.trim, newValue)
          }
          .getOrElse("")
      }
      .mkString(", ")

    List(argLine, withIndent(4, help))
  }
}

private[decline] case class EnvOptionHelp(name: String, metavar: String, help: String) {
  def show(theme: Theme): List[String] =
    List(theme.envName(name) + s"=<$metavar>", withIndent(4, help))
}
private[decline] case class CommandHelp(name: String, help: String) {
  def show(theme: Theme): List[String] =
    List(theme.subcommandName(name), withIndent(4, help))
}
