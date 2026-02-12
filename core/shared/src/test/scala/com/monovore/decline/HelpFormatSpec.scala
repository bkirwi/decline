package com.monovore.decline

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class HelpFormatSpec extends AnyWordSpec with Matchers {

  "HelpFormat" should {
    "respect NO_COLOR env variable in autoColors mode" in {
      val absent = Map.empty[String, String]
      val presentNonEmpty = Map("NO_COLOR" -> "1")
      val presentEmpty = Map("NO_COLOR" -> "")

      HelpFormat.autoColors(absent).colorsEnabled shouldEqual true
      HelpFormat.autoColors(presentNonEmpty).colorsEnabled shouldEqual false
      HelpFormat.autoColors(presentEmpty).colorsEnabled shouldEqual true

      // testing the autoColors forwarder added directly to Help
      Help.autoColors(absent).colorsEnabled shouldEqual true
      Help.autoColors(presentNonEmpty).colorsEnabled shouldEqual false
      Help.autoColors(presentEmpty).colorsEnabled shouldEqual true
    }
  }

}
