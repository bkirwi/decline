# decline

[![Build Status](https://img.shields.io/github/actions/workflow/status/bkirwi/decline/ci.yml?branch=main)](https://github.com/bkirwi/decline/actions)
[![Discord](https://img.shields.io/discord/632277896739946517.svg?label=&logo=discord&logoColor=ffffff&color=404244&labelColor=6A7EC2)](https://discord.com/channels/632277896739946517/895394320100761731)
[![Typelevel library](https://img.shields.io/badge/typelevel-library-green.svg)](https://typelevel.org/projects#decline)
[![decline Scala version support](https://index.scala-lang.org/bkirwi/decline/decline/latest-by-scala-version.svg?platform=jvm)](https://index.scala-lang.org/bkirwi/decline/decline)

A composable command-line parser, inspired by [`optparse-applicative`][optparse]
and built on [`cats`][cats].

```scala
import cats.syntax.all._
import com.monovore.decline._

object HelloWorld extends CommandApp(
  name = "hello-world",
  header = "Says hello!",
  main = {
    val userOpt =
      Opts.option[String]("target", help = "Person to greet.")
        .withDefault("world")

    val quietOpt = Opts.flag("quiet", help = "Whether to be quiet.").orFalse

    (userOpt, quietOpt).mapN { (user, quiet) => 

      if (quiet) println("...")
      else println(s"Hello $user!")
    }
  }
)
```

**To get started, please visit [monovore.com/decline](http://monovore.com/decline/)!**

## About the Project

`decline` is a [Typelevel](https://typelevel.org/projects/) project,
and follows the [Typelevel Scala Code of Conduct](https://typelevel.org/code-of-conduct).

This project is released under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

[optparse]: https://github.com/pcapriotti/optparse-applicative
[cats]: https://github.com/typelevel/cats
[decline]: http://ben.kirw.in/decline/
