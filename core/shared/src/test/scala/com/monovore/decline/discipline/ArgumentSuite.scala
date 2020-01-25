package com.monovore.decline.discipline

import cats.{instances, syntax, Eq, Show}
import org.typelevel.discipline.scalatest.FunSuiteDiscipline
import org.scalacheck.Arbitrary
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.Configuration

abstract class ArgumentSuite extends AnyFunSuite
  with FunSuiteDiscipline
  with Configuration
  with Matchers
  with instances.AllInstances
  with syntax.AllSyntax {

  protected final def checkArgument[A : Eq : Arbitrary : Show](name: String)(implicit tests: ArgumentTests[A]): Unit = {
    checkAll(s"Argument[$name]", tests.argument)
  }

}
