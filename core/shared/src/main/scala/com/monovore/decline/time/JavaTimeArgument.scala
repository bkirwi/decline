package com.monovore.decline.time

import cats.data.ValidatedNel
import cats.implicits._

import com.monovore.decline.Argument

private[time] abstract class JavaTimeArgument[A](val defaultMetavar: String) extends Argument[A] {
  protected def parseUnsafe(input: String): A

  override final def read(string: String): ValidatedNel[String, A] =
    Either.catchNonFatal(parseUnsafe(string)).leftMap(_.getMessage).toValidatedNel

}
