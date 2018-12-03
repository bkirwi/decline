---
layout: docs
title:  "Using Decline"
position: 2
---

# Using Decline

Welcome to `decline`!
Here, we'll run through all of `decline`'s major features and look at how they fit together.

`decline` is packaged under `com.monovore.decline`, so let's pull that in:

```tut:silent
import com.monovore.decline._
```

## Basic Options

'Normal' options take a single argument, with a specific type.
(It's important that you specify the type here;
the compiler usually can't infer it!)
This lets you parse options like the `-n50` in `tail -n50`.

```tut:book
val lines = Opts.option[Int]("lines", short = "n", metavar = "count", help = "Set a number of lines.")
```

Flags are similar, but take no arguments.
This is often used for 'boolean' flags,
like the `--quiet` in `tail --quiet`.

```tut:book
val quiet = Opts.flag("quiet", help = "Don't print any metadata to the console.")
```

Positional arguments aren't marked off by hyphens at all,
but they _do_ take a type parameter.
This handles arguments like the `file.txt` in `tail file.txt`.

```tut:book
import java.nio.file.Path

val file = Opts.argument[Path](metavar = "file")
```

Each of these option types has a plural form,
which are useful when you want users to pass the same kind of option multiple times.
Instead of just returning a value `A`,
repeated options and positional arguments will return a `NonEmptyList[A]`,
with all the values that were passed on the command line;
repeated flags will return the _number_ of times that flag was passed.

```tut:book
val settings = Opts.options[String]("setting", help = "...")
val verbose = Opts.flags("verbose", help = "Print extra metadata to the console.")
val files = Opts.arguments[String]("file")
```

You can also read a value directly from an environment variable.

```tut:book
val port = Opts.env[Int]("PORT", help = "The port to run on.")
```

## Time-based options

There is built-in support for Java 8 based datetime types like `Duration`, `ZonedDateTime`, `ZoneId`, etc in the
`com.monovore.decline.time` package which you need to import specifically to have access to:

```tut:silent
import java.time._
import com.monovore.decline.time._
```

And now you can use the Java 8 types as previously stated with any other supported type:

```tut:book
val fromDate = Opts.option[LocalDate]("fromDate", help = "Local date from where start looking at data")
val timeout = Opts.option[Duration]("timeout", help = "Operation timeout")
```

The out-of-box support is based on the ISO 8601 formats. If you would like to use these types in your command line
but with different formats/patterns, then check in the [arguments documentation](./arguments) for more information on how to do it.

_**NOTE**: Java 8 types are seamlessly supported in the JVM and in ScalaJS, the latter is achieved by making the ScalaJS module
for `decline` have a dependency in [`scala-java-time`](http://cquiroz.github.io/scala-java-time/)._

## Default Values

All of the above options are _required_: if they're missing, the parser will complain.
We can allow missing values with the `withDefault` method:

```tut:book
val linesOrDefault = lines.withDefault(10)
```

That returns a new `Opts[Int]` instance...
but this one can _always_ return a value,
whether or not `--lines` is passed on the command line.

There's a few more handy combinators for some particularly common cases:

```tut:book
val optionalFile = file.orNone
val fileList = files.orEmpty
val quietOrNot = quiet.orFalse
```

## Transforming and Validating

Like many other Scala types, `Opts` can be mapped over.

```tut:book
lines.map { _.toString }
```

`validate` is much like filter --
the parser will fail if the parsed value doesn't match the given function --
but it comes with a spot for a better error message.
`mapValidated` lets you validate and transform at once, since that's sometimes useful.

```tut:book
import cats.data.Validated

val validated = lines.validate("Must be positive!") { _ > 0 }

val both = lines.mapValidated { n =>
  if (n > 0) Validated.valid(n.toString)
  else Validated.invalidNel("Must be positive!")
}
```

## Combining Options

You can combine multiple `Opts` instances
using `cats`' [applicative syntax](http://typelevel.org/cats/typeclasses/apply.html#apply-builder-syntax):

```tut:book
import cats.implicits._

val tailOptions = (linesOrDefault, fileList).mapN { (n, files) =>
  println(s"LOG: Printing the last $n lines from each file in $files!")
}
```

Other options are mutually exclusive:
you might want to pass `--verbose` to make a command noisier,
or `--quiet` to make it quieter,
but it doesn't make sense to do both at once!

```tut:book
val verbosity = verbose orElse quiet.map { _ => -1 } withDefault 0
```

When you combine `Opts` instances with `orElse` like this,
the parser will choose the first alternative
that matches the given command-line arguments.

## Commands and Subcommands

A `Command` bundles up an `Opts` instance with some extra metadata,
like a command name and description.

```tut:book
val tailCommand = Command(
  name = "tail",
  header = "Print the last few lines of one or more files."
) {
  tailOptions
}
```

To embed the command as part of a larger application,
you can wrap it up as a _subcommand_.

```tut:book
val tailSubcommand = Opts.subcommand(tailCommand)

// or, equivalently and more concisely...

val tailSubcommand = Opts.subcommand("tail", help = "Print the few lines of one or more files.") {
  tailOptions
}
```

A subcommand is an instance of `Opts`...
and can be transformed, nested, and combined just like any other option type.
(If you're supporting multiple subcommands,
the `orElse` method is particularly useful:
`tailSubcommand orElse otherSubcommand orElse ...`.)

# Parsing Arguments

`Command`s aren't just useful for defining subcommands --
they're also used to parse an array of command-line arguments directly.
Calling `parse` returns either the parsed value, if the arguments were good,
or a help text if something went wrong.

```tut:book
tailCommand.parse(Seq("-n50", "foo.txt", "bar.txt"))
tailCommand.parse(Seq("--mystery-option"))
```

If your parser reads environment variables,
you'll want to pass in the environment as well.

```tut:book
tailCommand.parse(Seq("foo.txt"), sys.env)
```

A main method that uses `decline` for argument parsing would look something like:

```tut:book
def main(args: Array[String]) = tailCommand.parse(args, sys.env) match {
  case Left(help) =>
    System.err.println(help)
    sys.exit(1)
  case Right(parsedValue) =>
    // Your program goes here!
}
```

This handles arguments and environment variables correctly,
and reports any bad arguments clearly to the user.

# Using `CommandApp`

If you have a `Command[Unit]`,
extending `CommandApp` will wire up that main method for you.

```tut:book
object Tail extends CommandApp(tailCommand)
```

The resulting application can be called like any other Java app.

Instead of defining a separate command,
it's often easier to just define everything inline:

```tut:book
object Tail extends CommandApp(
  name = "tail",
  header = "Print the last few lines of one or more files.",
  main = (linesOrDefault, fileList).mapN { (n, files) =>
    println(s"LOG: Printing the last $n lines from each file in $files!")
  }
)
```

That's it!
To see a slightly larger example,
have a look at the [quick start](./).
