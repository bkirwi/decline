package com.monovore.clique
import com.monovore.clique.Parse.Result

trait Read[A] {
  def apply(string: String): Parse.Result[A]
}

object Read {

  def apply[A](string: String)(implicit read: Read[A]): Parse.Result[A] = read(string)

  implicit val readString: Read[String] = new Read[String] {
    override def apply(string: String): Parse.Result[String] = Parse.success(string)
  }

  implicit val readInt: Read[Int] = new Read[Int] {
    override def apply(string: String): Parse.Result[Int] =
      try { Parse.success(string.toInt) }
      catch { case nfe: NumberFormatException => Parse.failure(s"Invalid integer: $string") }
  }

  implicit val readLong: Read[Long] = new Read[Long] {
    override def apply(string: String): Parse.Result[Long] =
      try { Parse.success(string.toLong) }
      catch { case nfe: NumberFormatException => Parse.failure(s"Invalid integer: $string") }
  }
}
