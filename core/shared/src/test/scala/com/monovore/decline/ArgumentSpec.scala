package com.monovore.decline

import java.util.UUID

import com.monovore.decline.discipline.ArgumentSuite

class ArgumentSpec extends ArgumentSuite {

  test("check defer") {
    var cnt = 0
    val ai = Argument.declineArgumentDefer.defer{
      cnt += 1
      Argument[Int]
    }
    assert(cnt == 0)
    assert(ai.read("42").toOption == Some(42))
    assert(cnt == 1)
    assert(ai.read("314").toOption == Some(314))
    assert(cnt == 1)
  }

  checkArgument[String]("String")
  checkArgument[Int]("Int")
  checkArgument[Long]("Long")
  checkArgument[Short]("Short")
  checkArgument[BigInt]("BigInt")
  checkArgument[UUID]("UUID")

}
