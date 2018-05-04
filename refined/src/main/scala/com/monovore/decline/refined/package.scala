package com.monovore.decline

import cats.data.{Validated, ValidatedNel}
import eu.timepit.refined.api.{RefType, Validate}

package object refined {

  implicit def refTypeArgument[F[_, _], T, P](
      implicit argument: Argument[T],
      refType: RefType[F],
      validate: Validate[T, P]
  ): Argument[F[T, P]] = new Argument[F[T, P]] {

    override def read(string: String): ValidatedNel[String, F[T, P]] =
      argument.read(string) match {
        case Validated.Valid(t) =>
          refType.refine[P](t) match {
            case Left(reason) =>
              Validated.invalidNel(reason)

            case Right(refined) =>
              Validated.validNel(refined)
          }

        case Validated.Invalid(errs) =>
          Validated.invalid(errs)
      }

  }

}
