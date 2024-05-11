package com.monovore.decline

sealed trait HelpRenderer extends Product with Serializable
object HelpRenderer {
  case object Plain extends HelpRenderer
  case object Colors extends HelpRenderer
}
