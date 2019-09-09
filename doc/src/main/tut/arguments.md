---
layout: docs
title:  "Arguments"
position: 3
---

# Argument Types

In the [guide](/usage.html), we specified the type of an option's argument like so:

```tut:book
import com.monovore.decline._
import java.nio.file.Path

val path = Opts.option[Path]("input", "Path to the input file.")
```

This does two different things for us:

- It specifies a _parsing function_ -- when the user passes a string as an argument, `decline` will try and
  interpret it as a path and report an error if it can't.
- It specifies a default 'metavar' -- the `<path>` text you can see in the output above. This helps the user
  understand what sort of input your program expects in that position.
  
This information is provided by the `com.monovore.decline.Argument` [type class](https://typelevel.org/cats/typeclasses.html).
`decline` provides instances for many commonly used standard-library types: strings, numbers, paths, URIs...

## `java.time` support

`decline` has built-in support for the `java.time` library introduced in Java 8,
including argument instances for `Duration`, `ZonedDateTime`, `ZoneId`, `Instant`, and others.
To avoid breakage for those stuck on older Java versions,
you'll need to pull these in with an explicit import.

```tut:book
import java.time._, com.monovore.decline.time._

val fromDate = Opts.option[LocalDate]("fromDate", help = "Local date from where start looking at data")
val timeout = Opts.option[Duration]("timeout", help = "Operation timeout")
```

By default, this parses using the standard ISO 8601 formats.
If you'd like to use a custom time format,
`decline` also provides `Argument` builders that take a `java.time.format.DateTimeFormatter`.
For example, you can define a custom parse for a `LocalDate` by calling `localDateWithFormatter`:

```tut:book
import java.time.format.DateTimeFormatter
import com.monovore.decline.time.localDateWithFormatter

implicit val myDateArg: Argument[LocalDate] = localDateWithFormatter(
  DateTimeFormatter.ofPattern("dd/MM/yy")
)
```

## `refined` support

`decline` has support for [refined types](https://github.com/fthomas/refined) via the `decline-refined` module.
Refined types add an extra layer of safety by decorating standard types with predicates that get validated
automatically at compile time.
While command line arguments can't be validated at compile time,
refined argument types' runtime validation can still prevent
the introduction of invalid values by the user.

To make use of `decline-refined`, add the following to your `build.sbt`:

```scala
libraryDependencies += "com.monovore" %% "decline-refined" % "0.6.0"
```

As an example, let's define a simple refined type and use it as a command-line argument.

```tut:book
import eu.timepit.refined.api.Refined, eu.timepit.refined.numeric.Positive

type PosInt = Int Refined Positive

import com.monovore.decline.refined._

val lines = Command("lines", "Parse a positive number of lines.") {
  Opts.argument[PosInt]("count")
}
```

We can see that positive numbers will parse correctly, but anything zero or below will fail:

```tut:book
lines.parse(Seq("10"))
lines.parse(Seq("0"))
```

## `enumeratum` Support

`decline` also supports [enumeratum](https://github.com/lloydmeta/enumeratum) via the `decline-enumeratum` module.
Enumeratum provides a powerful Scala-idiomatic and Java-friendly implementation of enums.

To make use of the `enumeratum` support, add the following to your `build.sbt`:

```scala
libraryDependencies += "com.monovore" %% "decline-enumeratum" % "0.7.0"
```

Following there is a very simple example of a plain enum being used as a command line option. First of all,
we define the given enumeration as per required by `enumeratum`:

```tut:book
import enumeratum._
import com.monovore.decline.enumeratum._

sealed trait Color extends EnumEntry with EnumEntry.Lowercase
object Color extends Enum[Color] {
  case object Red extends Color
  case object Green extends Color
  case object Blue extends Color
    
  val values = findValues
}

val cmd = Command("color", "Return the chosen color.") {
  Opts.argument[Color]("color")
}
```

If we now try to parse a command line in which we have a `--color red` argument pair we should be able to parse
the `color` option:

```tut:book
cmd.parse(Seq("--color", "red"))
```

However if we pass a `black` as the argument (a value not part of the enum), the `parse` operation should fail:

```tut:book
cmd.parse(Seq("--color", "black"))
```

Note that because we have made the values lowercase (by mixing in the `EnumEntry.Lowercase` trait), if we pass a value
with the wrong character case, the parsing will fail too:

```tut:book
cmd.parse(Seq("--color", "Blue"))
```

## Using value enums

`enumeratum` also supports _value enums_, which are enumerations that are based on a value different than the actual
enum value name. This support needs a specific import from `enumeratum`:

```tut:silent
import enumeratum.values._
```

This is an example of a similar enum as before but being backed by an integer:

```tut:book
object valued {
  sealed abstract class IntColor(val value: Int) extends IntEnumEntry
  object IntColor extends IntEnum[IntColor] {
    case object Red extends IntColor(0)
    case object Green extends IntColor(1)
    case object Blue extends IntColor(2)

    val values = findValues
  }
}

import valued._
```

And now, as before, we define an option parameterised on that enum type and a command so we can test it:

```tut:book
val intColor = Opts.option[IntColor]("color", short = "c", metavar = "color", help = "Choose a color.")
val cmd = Command("showColor", "Shows the chosen color")(intColor)
```

If we now try to parse a command line in which we have a `--color red` argument pair we should be able to parse
the `color` option:

```tut:book
cmd.parse(Seq("--color", "0"))
```

Now, if we instead pass one of the options as text instead of as an `Int`, then the parser will fail:

```tut:book
cmd.parse(Seq("--color", "red"))
```

## Defining Your Own

In some cases, you'll want to take a command-line argument that doesn't quite map to a standard primitive type.
Say you have the following key-value config type:

```tut:book
case class Config(key: String, value: String)
```

It's easy enough to define an option that collects a list of configs, by specifying a
custom metavar and adding additional validation and parsing logic:

```tut:book
import cats.data.Validated

val config = {
  Opts.option[String]("config", "Specify an additional config.", metavar = "key:value")
    .mapValidated { string =>
      string.split(":", 2) match {
        case Array(key, value) => Validated.valid(Config(key, value))
        case _ => Validated.invalidNel(s"Invalid key:value pair: $string")
      }
    }
}
```

For most cases, this works perfectly well! For larger applications, though --
where many different options, subcommands or programs might want to use this
same basic config type -- doing this sort of thing each time is verbose and
error-prone.

It's easy enough to bundle the metavar and parsing logic together in an `Argument` instance:

```tut:book
implicit val configArgument: Argument[Config] = new Argument[Config] {

  def read(string: String) = {
    string.split(":", 2) match {
      case Array(key, value) => Validated.valid(Config(key, value))
      case _ => Validated.invalidNel(s"Invalid key:value pair: $string")
    }
  }

  def defaultMetavar = "key:value"
}
```

...and then defining new options that take configs becomes trivial:

```tut:book
val config = Opts.option[Config]("config", "Specify an additional config.")
```

## Missing Instances

In a few cases, `decline` has intentionally _not_ defined an `Argument` instance for a particular type -- since there
are better ways to achieve the same effect. Some examples:

- `Boolean`: supporting `Boolean` arguments like `Opts.option[Boolean]("verbose", ???)` would lead to command-line usage like 
  `my-command --verbose true`... but users of other POSIX-ish command line tools would expect `my-command --verbose`.
  You can get that more idiomatic style with `Opts.flag("verbose", ???).orFalse`; consider using that instead!
- `java.io.File`, `java.net.URL`: these types are mostly superseded by better alternatives (`java.nio.file.Path` and
  `java.net.URI`, respectively), and they support easy conversions to the older types to interoperate with existing code.
- `List[A]`: you might expect to be able to define a `Opts.option[List[String]](...)` to parse a comma-separated list of
  strings, like `--exclude foo,bar`. This ends up a little bit tricky in the general case: either you can't parse strings
  that contain commas, or you need some "escaping" mechanism, neither of which is particularly pleasant or idiomatic for
  users. Instead, consider using the plural methods like `Opts.options` or `Opts.arguments` to accumulate a list,
  like `--exclude foo --exclude bar`. (This is also easier to use programatically!)