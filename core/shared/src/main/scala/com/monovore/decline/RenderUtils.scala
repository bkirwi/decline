package com.monovore.decline

private[decline] object RenderUtils {
  def withIndent(indent: Int, s: String): String =
    // Predef.augmentString = work around scala/bug#11125
    augmentString(s).linesIterator.map(" " * indent + _).mkString("\n")

  def withIndent(indent: Int, lines: List[String]): List[String] =
    lines.map(line => withIndent(indent, line))

  def intersperseList[A](xs: List[A], x: A): List[A] = {
    val bld = List.newBuilder[A]
    val it = xs.iterator
    if (it.hasNext) {
      bld += it.next()
      while (it.hasNext) {
        bld += x
        bld += it.next()
      }
    }
    bld.result()
  }
}
