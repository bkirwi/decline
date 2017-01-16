---
layout: home
title:  "Home"
position: 1
---

# decline

A composable command-line parser, inspired by [`optparse-applicative`][optparse]
and built on [`cats`][cats].

# Quick Start

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

[optparse]: https://github.com/pcapriotti/optparse-applicative
[cats]: https://github.com/typelevel/cats