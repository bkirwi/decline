package com.monovore.decline

sealed abstract class HelpFormat {
  def colorsEnabled: Boolean
}
object HelpFormat {
  case object Plain extends HelpFormat {
    override def colorsEnabled: Boolean = false
  }

  case object Colors extends HelpFormat {
    override def colorsEnabled: Boolean = true
  }

  def autoColors(env: Map[String, String]) =
    new HelpFormat {
      override def colorsEnabled: Boolean = env.get("NO_COLOR").exists(_.nonEmpty)
    }
}
