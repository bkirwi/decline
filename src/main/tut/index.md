---
layout: home
title:  "Home"
position: 1
---

# decline

A composable command-line parser, inspired by [`optparse-applicative`][optparse]
and built on [`cats`][cats].

# Quick Start

First, pull the library into your build. For `sbt`:

```scala
// Artifacts are published to bintray.
resolvers += Resolver.bintrayRepo("bkirwi", "maven")

// `decline` is available for both 2.11 and 2.12
libraryDepenencies += "com.monovore" %% "decline" % "0.1"
```

Then, write a program:

```tut:silent
import cats.implicits._
import com.monovore.decline._

object HelloWorld extends CommandApp(
  name = "hello-world",
  header = "Says hello!",
  main = {
    val userOpt = 
      Opts.option[String]("target", help = "Person to greet.")
        .withDefault("world")
    
    val quietOpt = Opts.flag("quiet", help = "Whether to be quiet.").orFalse

    (userOpt |@| quietOpt).map { (user, quiet) => 

      if (quiet) println("...")
      else println(s"Hello $user!")
    }
  }
)
```

Then, run it:

```
$ hello-world --help
Usage: hello-world [--target <name>] [--quiet]

Says hello!

    --target <name>
            Person to greet.
    --quiet
            Whether to be quiet.
    --help
            Display this help text.
$ hello-world --target friend
Hello, friend!
```

(For a more in-depth introduction, see the [user's guide](usage.html)!)

[optparse]: https://github.com/pcapriotti/optparse-applicative
[cats]: https://github.com/typelevel/cats