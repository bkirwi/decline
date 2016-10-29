package com.monovore

import cats.data.Validated

package object decline {

  /** A standard result type for reading and validating command-line options.
    *
    * Why return a list of errors? If there are multiple problems with the
    * arguments, we want to list all of them, and we sometimes want to 'fail'
    * even when there are no errors at all! (For example, if the user requests
    * --help, we should exit abnormally but not print any errors.)
    */
  type Result[A] = Validated[List[String], A]

  object Result {
    def success[A](a: A): Result[A] = Validated.valid(a)
    def failure[A](reasons: String*): Result[A] = Validated.invalid(reasons.toList)
  }
}
