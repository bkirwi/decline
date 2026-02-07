package com.monovore.decline

private[decline] object Messages {
  val UnexpectedOptionPrefix = "Unexpected option:"
  val UnexpectedArgumentPrefix = "Unexpected argument:"
  val AmbiguousOptionFlagPrefix = "Ambiguous option/flag:"
  val MissingValueForOptionPrefix = "Missing value for option:"
  val UnexpectedValueForFlagPrefix = "Got unexpected value for flag:"

  def unexpectedOption(name: String): String = s"$UnexpectedOptionPrefix $name"
  def unexpectedArgument(arg: String): String = s"$UnexpectedArgumentPrefix $arg"
  def ambiguousOptionFlag(name: String): String = s"$AmbiguousOptionFlagPrefix $name"
  def missingValueForOption(name: String): String = s"$MissingValueForOptionPrefix $name"
  def unexpectedValueForFlag(name: String): String = s"$UnexpectedValueForFlagPrefix $name"
}
