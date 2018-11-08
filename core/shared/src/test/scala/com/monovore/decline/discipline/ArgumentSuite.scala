package com.monovore.decline.discipline

import cats.tests.CatsSuite
import cats.{Eq, Show}
import org.scalacheck.Arbitrary

abstract class ArgumentSuite extends CatsSuite {

  protected final def checkArgument[A : Eq : Arbitrary : Show](name: String)(implicit tests: ArgumentTests[A]): Unit = {
    checkAll(s"Argument[$name]", tests.argument)
  }

}
