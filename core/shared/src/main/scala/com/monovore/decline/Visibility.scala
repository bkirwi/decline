package com.monovore.decline

sealed abstract class Visibility

object Visibility {

  /** Show in both the usage and help text. */
  case object Normal extends Visibility

  /** Show in the full help listing, but not in usage or suggestions. */
  case object Partial extends Visibility
}
