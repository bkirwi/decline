package com.monovore.decline

private[decline] trait BinCompat {
  @deprecated(
    "This method should not be used and is only left for binary compatibility reasons",
    "2.6.0"
  )
  def unapply(
      h: Help
  ): Option[(List[String], cats.data.NonEmptyList[String], List[String], List[String])] =
    Some(
      h.errors,
      h.prefix,
      h.usage,
      h.body
    )

}
