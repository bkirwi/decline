# decline

[![Build Status](https://img.shields.io/github/workflow/status/bkirwi/decline/Continuous%20Integration.svg)](https://github.com/bkirwi/decline/actions)

A composable command-line parser, inspired by [`optparse-applicative`][optparse]
and built on [`cats`][cats].

```scala
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

    (userOpt, quietOpt).mapN { (user, quiet) => 

      if (quiet) println("...")
      else println(s"Hello $user!")
    }
  }
)
```

**To get started, please visit [monovore.com/decline](http://monovore.com/decline/)!**

## About the Project

`decline` is a [Typelevel Incubator](https://typelevel.org/projects/) project,
and follows the [Scala Code of Conduct](https://typelevel.org/code_of_conduct.html).

This project is released under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).

[optparse]: https://github.com/pcapriotti/optparse-applicative
[cats]: https://github.com/typelevel/cats
[decline]: http://ben.kirw.in/decline/
