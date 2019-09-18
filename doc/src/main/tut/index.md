---
layout: home
title:  "Home"
position: 1
---

`decline` is a composable command-line parsing library,
inspired by [`optparse-applicative`][optparse]
and built on [`cats`][cats].

# Why `decline`?

- **Full-featured:**
  `decline` supports the standard set of Unix command-line idioms,
  including flags, options, positional arguments, and subcommands.
  Support for mutually-exclusive options and custom validations
  make it easy to mold your CLI to the shape of your application.
- **Helpful:**
  `decline` automatically generates
   comprehensive and precise error messages and usage texts.
- **Functional:**
  `decline` provides an immutable and functional API,
  usable whether or not your program is written in a functional style.

# Quick Start

First, pull the library into your build. For `sbt`:

```scala
libraryDependencies += "com.monovore" %% "decline" % "1.0.0"
```

Then, write a program:

```scala mdoc:silent
import cats.implicits._
import com.monovore.decline._

object HelloWorld extends CommandApp(
  name = "hello-world",
  header = "Says hello!",
  main = {
    val userOpt =
      Opts.option[String]("target", help = "Person to greet.").withDefault("world")

    val quietOpt = Opts.flag("quiet", help = "Whether to be quiet.").orFalse

    (userOpt, quietOpt).mapN { (user, quiet) =>
      if (quiet) println("...")
      else println(s"Hello $user!")
    }
  }
)
```

Then, run it:

```
$ hello-world --help
Usage: hello-world [--target <string>] [--quiet]

Says hello!

Options and flags:
    --help
        Display this help text.
    --target <string>
        Person to greet.
    --quiet
        Whether to be quiet.

$ hello-world --target friend
Hello, friend!
```

(For a more in-depth introduction, see the [user's guide](usage.html)!)

[optparse]: https://github.com/pcapriotti/optparse-applicative
[cats]: https://github.com/typelevel/cats
