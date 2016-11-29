# decline

A composable command-line parser, inspired by [`optparse-applicative`][optparse]
and built on [`cats`][cats].

# An Example

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

    (userOpt |@| quietOpt).map { (user, quiet) => 

      if (quiet) println("...")
      else println(s"Hello $user!")
    }
  }
)
```

```
$ hello-world --help
Usage: hello-world [--target <name>] [--quiet] [--help]

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

# How It Works

## Types

`Opts[A]` is the most important type in `decline`. You can think of it as a bit
like `List[String] => A`, but with enough extra structure to generate a useful
help text. The `Opts` companion object provides a bunch of helpers for defining
options; we'll go into more detail on that below.

A `Command[A]` _contains_ an `Opts[A]`, along with enough extra metadata to
describe a full program. It's used by `CommandApp` to describe an application,
and as a way to support subcommands in the parser.

`Validated` is a data type, provided by `cats`, that's commonly used for
accumulating errors. `decline` defines a type alias -- `type Result[A] =
Validated[List[String], A]`, which is used for accumulating errors in the parse.

## Defining Options

`decline` supports several common types of command-line options.

An *option* is a command line argument thats starts with `-`. Most options take
a value; options that don't are called *flags*. `decline` also supports
*positional arguments*, which are not marked with a `-`, and are matched based
only on their position in the argument list.

In general, most idioms of GNU-style option parsing should be supported:
`decline` supports both long and short options, and handles the special `--`
argument that marks the end of the option list.

```scala
// An integral option, as in `head --count 20`. (Or, equivalently, `head -n20`.)
val option: Opts[Int] = Opts.option[Int]("lines", short="n", help = "...")

// A flag: true if present, false if absent.
val flag: Opts[Unit] = Opts.flag("name", "...")

// A positional argument. We can specify a metavar here as well.
val positional: Opts[String] = Opts.argument[String](metavar = "filename")

// The standard map function works on `Opts`.
val mapped = option.map { int => int.toString }

// We can apply a validation: if it returns false, parsing will fail
val validated = option.validate("Must be positive!") { _ > 0 }

// We use cats' Validated type to collect errors. The mapValidated method
// lets you transform and validate at once.
val both = option.mapValidated { int =>
  if (int > 0) Validated.valid(int.toString)
  else Validated.invalid(List(s"Must be positive!"))
}
```

If we want our software to take more than one option, we need a way of combining
multiple `Opts` instances together. Since `Opts` is `Applicative`, the simplest
way to do this is to lean on `cats`' existing applicative syntax.

```scala
import cats.implicits._

opts
val stringOpt: Opts[String] = ???
val intOpt: Opts[Int] = ???

// ...we can use the |@| syntax to get at both values together.
val combined = (stringOpt |@| intOpt).map { (string, int) =>
  ???
}
```

`decline` can also handle `git`-style subcommands:

```scala
val buildOpt: Opts[Unit] = ???
val cleanOpt: Opts[Unit] = ???

val subcommands: Opts[Unit] =
  Opts.command("build", help = "...") { buildOpt } orElse
  Opts.command("clean", help = "...") { cleanOpt }

# Pulling it Together

Given a list of arguments, `Opts.parse(argumentList)` returns the parse result.

However, we've already noted that `Opts[A]` is roughly analogous to
`List[String] => A`: this implies that `Opts[Unit]` corresponds to `List[String]
=> Unit`, which is close to the standard `def main(args: Array[String]): Unit`
type signature Scala defines for applications. If you've bottled up your
application as an `Opts[Unit]`, `decline` provides a `CommandApp` class that can
wire up the right main method for you.

```scala
import com.monovore.decline._

object MyApp extends CommandApp(
  name = "my-app",
  header = "Header text for the help output",
  main = {
    Opts.remainingArgs[String]() map { args =>
      println(args)
    }
  }
)
