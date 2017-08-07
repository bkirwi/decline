# decline

[![Build Status](https://travis-ci.org/bkirwi/decline.svg?branch=master)](https://travis-ci.org/bkirwi/decline)

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

[optparse]: https://github.com/pcapriotti/optparse-applicative
[cats]: https://github.com/typelevel/cats
[decline]: http://ben.kirw.in/decline/
