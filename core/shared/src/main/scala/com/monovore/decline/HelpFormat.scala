package com.monovore.decline

sealed abstract class HelpFormat extends Product with Serializable {
  def colorsEnabled: Boolean
}
object HelpFormat {
  case object Plain extends HelpFormat {
    override def colorsEnabled: Boolean = false
  }
  case object Colors extends HelpFormat {
    override def colorsEnabled: Boolean = true
  }
  case class AutoColors(env: Map[String, String] = sys.env) extends HelpFormat {
    /*

      http://no-color.org/

      "Command-line software which adds ANSI color to its output by default should
      check for a NO_COLOR environment variable that, when present and not an empty
      string (regardless of its value), prevents the addition of ANSI color."

     */

    override def colorsEnabled: Boolean = env.get("NO_COLOR").exists(_.nonEmpty)

  }
}
