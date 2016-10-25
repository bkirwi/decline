# decline

A composable command-line parser.

```scala
import cats.implicits._
import com.monovore.decline._

object HelloWorld extends CommandApp(
  name = "hello-world",
  header = "Say hello to a person or world.",
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
hello-world --help

Usage: hello-world [--target=NAME] [--quiet] [--help]

Say hello to a person or world.

    --target=NAME
            Person to greet.
    --quiet
            Whether to be quiet.
    --help
            Display this help text
```

[optparse]: https://github.com/pcapriotti/optparse-applicative
[cats]: https://github.com/typelevel/cats
