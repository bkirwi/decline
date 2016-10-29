package com.monovore.decline

trait Read[A] {
  def apply(string: String): Result[A]
}

object Read {

  def apply[A](string: String)(implicit read: Read[A]): Result[A] = read(string)

  implicit val readString: Read[String] = new Read[String] {
    override def apply(string: String): Result[String] = Result.success(string)
  }

  implicit val readInt: Read[Int] = new Read[Int] {
    override def apply(string: String): Result[Int] =
      try { Result.success(string.toInt) }
      catch { case nfe: NumberFormatException => Result.failure(s"Invalid integer: $string") }
  }

  implicit val readLong: Read[Long] = new Read[Long] {
    override def apply(string: String): Result[Long] =
      try { Result.success(string.toLong) }
      catch { case nfe: NumberFormatException => Result.failure(s"Invalid integer: $string") }
  }
}
