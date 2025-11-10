package com.monovore.decline

private[decline] trait BinCompat extends scala.deriving.Mirror.Product {
  type MirroredMonoType = Help

  @deprecated(
    "This method should not be used and is only left for binary compatibility reasons",
    "2.6.0"
  )
  def unapply(
      h: Help
  ): Help = h

  def fromProduct(p: scala.Product): Help =
    Help(
      p.productElement(0).asInstanceOf[List[String]],
      p.productElement(1).asInstanceOf[cats.data.NonEmptyList[String]],
      p.productElement(2).asInstanceOf[List[String]],
      p.productElement(3).asInstanceOf[List[String]]
    )
}
