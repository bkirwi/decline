package com.monovore.decline

import cats.data.{Validated, ValidatedNel}

import _root_.enumeratum._
import _root_.enumeratum.values._

import scala.reflect.ClassTag

package object enumeratum {

  implicit def enumeratumEnumEntryArgument[A <: EnumEntry](implicit enum: Enum[A], ct: ClassTag[A]): Argument[A] =
    new Argument[A] {
      override def defaultMetavar: String = ct.runtimeClass.getSimpleName().toLowerCase()

      override def read(string: String): ValidatedNel[String, A] = {
        enum.withNameOption(string) match {
          case Some(v) => Validated.validNel(v)
          case None    => Validated.invalidNel(s"Invalid value: $string")
        }
      }
    }

  implicit def enumeratumIntEnumEntryArgument[A <: IntEnumEntry](implicit enum: IntEnum[A], ct: ClassTag[A]): Argument[A] =
    new ValueEnumArgument(enum, Argument[Int], ct)

  implicit def enumeratumLongEnumEntryArgument[A <: LongEnumEntry](implicit enum: LongEnum[A], ct: ClassTag[A]): Argument[A] =
    new ValueEnumArgument(enum, Argument[Long], ct)

  // implicit def enumeratumShortEnumEntryArgument[A <: ShortEnumEntry](implicit enum: ShortEnum[A], ct: ClassTag[A]): Argument[A] =
  //   new ValueEnumArgument(enum, Argument[Short], ct)

  implicit def enumeratumStringEnumEntryArgument[A <: StringEnumEntry](implicit enum: StringEnum[A], ct: ClassTag[A]): Argument[A] =
    new ValueEnumArgument(enum, Argument[String], ct)

}