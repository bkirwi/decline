package com.monovore.decline

import java.nio.file.Path

private[decline] abstract class PlatformMetavars {
  implicit lazy val pathMetavar = Metavar.instance[Path]("path")
}
