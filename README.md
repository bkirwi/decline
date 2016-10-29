# decline

A composable command-line parser, inspired by [`optparse-applicative`][optparse]
and built on [`cats`][cats].

```scala
import cats.implicits._
import com.monovore.decline._

object HelloWorld extends CommandApp(
  name = "hello-world",
  header = "Says hello!",
  options = {
    val userOpt = 
      Opts.optional[String]("target", metavar = "NAME", help = "Person to greet.")
        .withDefault("world")
    
    val quietOpt = Opts.flag("quiet", help = "Whether to be quiet.")

    (userOpt |@| quietOpt).map { (user, quiet) => 

      if (quiet) println("...")
      else println(s"Hello $user!")
    }
  }
)
```

```
$ hello-world --help
Usage: hello-world [--target=NAME] [--quiet] [--help]

Says hello!

    --target=NAME
            Person to greet.
    --quiet
            Whether to be quiet.
    --help
            Display this help text
```

[optparse]: https://github.com/pcapriotti/optparse-applicative
[cats]: https://github.com/typelevel/cats
