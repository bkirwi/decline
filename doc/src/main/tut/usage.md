---
layout: docs
title:  "Using Decline"
position: 2
---

# Using Decline

Welcome to `decline`!
Here, we'll run through all of `decline`'s major features and look at how they fit together.

`decline` is packaged under `com.monovore.decline`, so let's pull that in:

```scala mdoc
import com.monovore.decline._
```

## Basic Options

'Normal' options take a single argument, with a specific type.
(It's important that you specify the type here;
the compiler usually can't infer it!)
This lets you parse options like the `-n50` in `tail -n50`.

```scala mdoc:to-string
val lines = Opts.option[Int]("lines", short = "n", metavar = "count", help = "Set a number of lines.")
```

Flags are similar, but take no arguments.
This is often used for 'boolean' flags,
like the `--quiet` in `tail --quiet`.

```scala mdoc:to-string
val quiet = Opts.flag("quiet", help = "Don't print any metadata to the console.")
```

Positional arguments aren't marked off by hyphens at all,
but they _do_ take a type parameter.
This handles arguments like the `file.txt` in `tail file.txt`.

```scala mdoc:to-string
import java.nio.file.Path

val file = Opts.argument[Path](metavar = "file")
```

Each of these option types has a plural form,
which are useful when you want users to pass the same kind of option multiple times.
Instead of just returning a value `A`,
repeated options and positional arguments will return a `NonEmptyList[A]`,
with all the values that were passed on the command line;
repeated flags will return the _number_ of times that flag was passed.

```scala mdoc:to-string
val settings = Opts.options[String]("setting", help = "...")

val verbose = Opts.flags("verbose", help = "Print extra metadata to the console.")

val files = Opts.arguments[String]("file")
```

You can also read a value directly from an environment variable.

```scala mdoc:to-string
val port = Opts.env[Int]("PORT", help = "The port to run on.")
```

## Default Values

All of the above options are _required_: if they're missing, the parser will complain.
We can allow missing values with the `withDefault` method:

```scala mdoc:to-string
val linesOrDefault = lines.withDefault(10)
```

That returns a new `Opts[Int]` instance...
but this one can _always_ return a value,
whether or not `--lines` is passed on the command line.

There's a few more handy combinators for some particularly common cases:

```scala mdoc:to-string
val optionalFile = file.orNone

val fileList = files.orEmpty

val quietOrNot = quiet.orFalse
```

## Transforming and Validating

Like many other Scala types, `Opts` can be mapped over.

```scala mdoc:to-string
lines.map { _.toString }
```

`validate` is much like filter --
the parser will fail if the parsed value doesn't match the given function --
but it comes with a spot for a better error message.
`mapValidated` lets you validate and transform at once, since that's sometimes useful.

```scala mdoc:to-string
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

```scala mdoc:to-string
import cats.syntax.all._

val tailOptions = (linesOrDefault, fileList).mapN { (n, files) =>
  println(s"LOG: Printing the last $n lines from each file in $files!")
}
```

[`tupled`](https://github.com/typelevel/cats/blob/69356ef/project/Boilerplate.scala#L136) is a useful operation when you want
to compose into a larger `Opts` that yields a tuple:

```scala mdoc:to-string
import cats.syntax.all._

val tailOptionsTuple = (linesOrDefault, fileList).tupled
```

Other options are mutually exclusive:
you might want to pass `--verbose` to make a command noisier,
or `--quiet` to make it quieter,
but it doesn't make sense to do both at once!

```scala mdoc:to-string
val verbosity = verbose orElse quiet.map { _ => -1 } withDefault 0
```

When you combine `Opts` instances with `orElse` like this,
the parser will choose the first alternative
that matches the given command-line arguments.

## Commands and Subcommands

A `Command` bundles up an `Opts` instance with some extra metadata,
like a command name and description.

```scala mdoc:to-string
val tailCommand = Command(
  name = "tail",
  header = "Print the last few lines of one or more files."
) {
  tailOptions
}
```

To embed the command as part of a larger application,
you can wrap it up as a _subcommand_.

```scala mdoc:to-string
val tailSubcommand = Opts.subcommand(tailCommand)
```

... or, equivalently and more concisely...

```scala mdoc:to-string
val tailSubcommand2 = Opts.subcommand("tail", help = "Print the few lines of one or more files.") {
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

```scala mdoc:to-string
tailCommand.parse(Seq("-n50", "foo.txt", "bar.txt"))
tailCommand.parse(Seq("--mystery-option"))
```

If your parser reads environment variables,
you'll want to pass in the environment as well.

```scala mdoc:to-string
tailCommand.parse(Seq("foo.txt"), sys.env)
```

A main method that uses `decline` for argument parsing would look something like:

```scala mdoc:to-string
def main(args: Array[String]) = tailCommand.parse(args, sys.env) match {

  case Left(help) if help.errors.isEmpty =>
    // help was requested by the user, i.e.: `--help`
    println(help)
    sys.exit(0)

  case Left(help) =>
    // user needs help due to bad/missing arguments
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

```scala mdoc:to-string
object Tail extends CommandApp(tailCommand)
```

The resulting application can be called like any other Java app.

Instead of defining a separate command,
it's often easier to just define everything inline:

```scala mdoc:to-string
object TailApp extends CommandApp(
  name = "tail",
  header = "Print the last few lines of one or more files.",
  main = (linesOrDefault, fileList).mapN { (n, files) =>
    println(s"LOG: Printing the last $n lines from each file in $files!")
  }
)
```

That's it!
If you made it this far,
you might be interested in [more supported argument types](./arguments.html),
[`cats-effect` integration](./effect.html) or [Scala.js support](./scalajs.html).
