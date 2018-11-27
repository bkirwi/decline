package com.monovore.decline

import java.nio.file.{Path, Paths}

import com.monovore.decline.discipline.ArgumentSuite

class ArgumentSpec extends ArgumentSuite {

  checkArgument[String]("String")
  checkArgument[Int]("Int")
  checkArgument[Long]("Long")
  checkArgument[BigInt]("BigInt")

}
