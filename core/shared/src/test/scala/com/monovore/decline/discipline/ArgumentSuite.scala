package com.monovore.decline.discipline

import cats.{instances, syntax, Eq, Show}
import org.typelevel.discipline.scalatest.Discipline
import org.scalacheck.Arbitrary
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.Matchers

abstract class ArgumentSuite extends AnyFunSuite
  with Discipline
  with Matchers
  with instances.AllInstances
  with syntax.AllSyntax {

  protected final def checkArgument[A : Eq : Arbitrary : Show](name: String)(implicit tests: ArgumentTests[A]): Unit = {
    checkAll(s"Argument[$name]", tests.argument)
  }

}
