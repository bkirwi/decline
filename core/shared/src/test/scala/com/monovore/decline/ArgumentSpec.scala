package com.monovore.decline

import java.util.UUID

import com.monovore.decline.discipline.ArgumentSuite

class ArgumentSpec extends ArgumentSuite {

  checkArgument[String]("String")
  checkArgument[Int]("Int")
  checkArgument[Long]("Long")
  checkArgument[Short]("Short")
  checkArgument[BigInt]("BigInt")
  checkArgument[UUID]("UUID")

}
