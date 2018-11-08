package com.monovore.decline.discipline

import cats.data.{Validated, ValidatedNel}
import cats.implicits._
import cats.laws._
import cats.laws.discipline._
import cats.{Eq, Show}
import com.monovore.decline.Argument
import org.scalacheck.{Arbitrary, Prop}
import org.typelevel.discipline.Laws

trait ArgumentLaws[A] {
  def arg: Argument[A]

  def passThrough(a: A)(implicit showA: Show[A]): IsEq[ValidatedNel[String, A]] =
    arg.read(showA.show(a)) <-> Validated.validNel(a)
}

object ArgumentLaws {
  def apply[A](implicit argA: Argument[A]): ArgumentLaws[A] = new ArgumentLaws[A] {
    val arg: Argument[A] = argA
  }
}

trait ArgumentTests[A] extends Laws {
  def laws: ArgumentLaws[A]

  def argument(implicit arbitraryA: Arbitrary[A], eqA: Eq[A], showA: Show[A]): RuleSet = new DefaultRuleSet(
    name = "argument",
    parent = None,
    "passThrough" -> Prop.forAll { (a: A) =>
      laws.passThrough(a)
    }
  )
}
object ArgumentTests {
  implicit def apply[A: Argument]: ArgumentTests[A] = new ArgumentTests[A] {
    val laws: ArgumentLaws[A] = ArgumentLaws[A]
  }
}