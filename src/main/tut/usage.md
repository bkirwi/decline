---
layout: docs
title:  "Using Decline"
position: 2
---

```tut:silent
import com.monovore.decline.{Command, CommandApp, Opts, Result}
```

`Opts[A]` represents one or more command-line options that, when parsed, produce a value of type `A`.

## Basic Options
`decline` supports three different types of options.
'Normal' options take a single argument, with a specific type.
(It's important that you specify the type here;
the compiler usually can't infer it!)
This lets you parse options like the `--lines` in `tail --lines 50`.

```tut:book
val lines = Opts.option[Int]("lines", short = "n", help = "Set a number of lines.")
```

Flags are similar, but take no arguments.
This is pretty common for boolean flags,
like the `-f` passed to `tail -f` when continuously tailing a file.

```tut:book
val follow = Opts.flag("follow", short = "f", help = "Continuously output data as the file grows.")
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
val verbose = Opts.flags("verbose", help = "...")
val files = Opts.arguments[String]("file")
```

## Default Values

All of the above options are _required_: if they're missing, the parser will complain.
One way to allow missing values is the `withDefault` method.

```tut:book
val linesOrDefault = lines.withDefault(10)
```

That returns a new `Opts[Int]` instance...
but this one that can _always_ return a value, whether or not `--lines` is passed on the command line.

There's a few more handy combinators for some particularly common cases:

```tut:book
file.orNone
files.orEmpty
follow.orFalse
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
val validated = lines.validate("Must be positive!") { _ > 0 }

val both = lines.mapValidated { n =>
  if (n > 0) Result.success(n.toString)
  else Result.failure("Must be positive!")
}
```

## Combining Options

You can combine multiple `Opts` instances
using `cats`' [applicative syntax](http://typelevel.org/cats/typeclasses/apply.html#apply-builder-syntax):

```tut:book
import cats.implicits._

val tailOptions = (linesOrDefault |@| files).map { (n, files) =>
  println(s"LOG: Printing the last $n lines from each file in $files!")
}
```

Other options are mutually exclusive:
you might want to pass `--verbose` to make a command noisier,
or `--quiet` to make it quieter,
but it doesn't make sense to do both at once!

```tut:book
val verboseOrQuiet =
  verbose orElse Opts.flag("quiet", "Run quietly.").map { _ => -1 }
```

## Commands and Subcommands

A `Command` bundles up an `Opts` instance with some extra metadata,
like a command name and description.

```tut:book
val tailCommand = Command(
  name = "tail",
  header = "Print the last few lines of one or more files.",
  options = tailOptions
)
```

That's enough info to parse command-line args!
The `parse` method returns either the parsed value, if the arguments were good,
or a help text if something went wrong.

```tut:book
tailCommand.parse(Seq("-n50", "foo.txt", "bar.txt"))
tailCommand.parse(Seq("--mystery-option"))
```

If you're embedding the command as part of a larger application,
you can wrap it up as a _subcommand_.

```tut:book
val tailSubcommand = Opts.subcommand(tailCommand)

// or, equivalently and more concisely...

val tailSubcommand = Opts.subcommand("tail", help = "Print the few lines of one or more files.")(tailOptions)
```

A subcommand is an instance of `Opts`...
and can be transformed and combined just like any other option type.
(If you're supporting multiple subcommands,
the `orElse` method is particularly useful:
`tailSubcommand orElse otherSubcommand orElse ...`.)

If you have a `Command[Unit]`,
extending `CommandApp` will wire it up to a main method for you.

```tut:book
object Tail extends CommandApp(tailCommand)
```

...and we're done!
For a example of everything all together,
have a look at the [quick start](/).