package com.monovore.decline

import cats.Show
import cats.data.NonEmptyList
import cats.syntax.all._
import com.monovore.decline.HelpFormat.Plain
import com.monovore.decline.HelpFormat.Colors

private[decline] trait Theme {
  def sectionHeading(title: String): String = title
  def subcommandName(title: String): String = title
  def optionName(title: String, loc: Theme.ArgumentRenderingLocation): String = title
  def metavar(title: String, loc: Theme.ArgumentRenderingLocation): String = title
  def envName(title: String): String = title
  def optionPlaceholder(title: String, loc: Theme.ArgumentRenderingLocation): String = title
  def optionDescription(value: String): String = value
  def error(value: String): String = value
}

private[decline] object Theme {
  sealed trait ArgumentRenderingLocation extends Product with Serializable
  object ArgumentRenderingLocation {
    case object InUsage extends ArgumentRenderingLocation
    case object InOptions extends ArgumentRenderingLocation
  }
  def forRenderer(hr: HelpFormat): Theme =
    if (hr.colorsEnabled) ColorTheme else PlainTheme
}

private[decline] object PlainTheme extends Theme

private[decline] object ColorTheme extends Theme {
  override def sectionHeading(title: String): String =
    Console.YELLOW + Console.BOLD + title + Console.RESET

  override def optionName(title: String, loc: Theme.ArgumentRenderingLocation): String =
    Console.BOLD + Console.GREEN + title + Console.RESET

  override def metavar(title: String, loc: Theme.ArgumentRenderingLocation): String =
    Console.UNDERLINED + title + Console.RESET

  override def envName(title: String): String =
    Console.BOLD + Console.GREEN + title + Console.RESET

  override def subcommandName(title: String): String =
    Console.BOLD + Console.GREEN + title + Console.RESET

  override def error(title: String): String =
    Console.BOLD + Console.RED + title + Console.RESET
}
