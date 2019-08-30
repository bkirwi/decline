package com.monovore.decline
package enumeratum

import cats.data.{Validated, ValidatedNel}

import _root_.enumeratum.values._

private[enumeratum] final class ValueEnumArgument[A, Entry <: ValueEnumEntry[A]](
    enum: ValueEnum[A, Entry],
    baseArgument: Argument[A]
  ) extends Argument[Entry] {
  
  override def defaultMetavar: String = baseArgument.defaultMetavar

  override def read(string: String): ValidatedNel[String, Entry] = {
    baseArgument.read(string) match {
      case inv @ Validated.Invalid(_) => inv
      case Validated.Valid(value) =>
        enum.withValueOpt(value) match {
          case Some(r) => Validated.validNel(r)
          case None    => Validated.invalidNel(s"Invalid value: $string")
        }
    }
  }
}