---
layout: docs
title:  "Arguments"
position: 3
---

# Argument Types

In the [guide](./usage.html), we specified the type of an option's argument like so:

```scala mdoc:to-string
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
You'll need to pull these in with an explicit import:

```scala mdoc:to-string
import java.time._
import com.monovore.decline.time._

val fromDate = Opts.option[LocalDate]("fromDate", help = "Local date from where start looking at data")
val timeout = Opts.option[Duration]("timeout", help = "Operation timeout")
```

By default, this parses using the standard ISO 8601 formats.
If you'd like to use a custom time format,
`decline` also provides `Argument` builders that take a `java.time.format.DateTimeFormatter`.
For example, you can define a custom parse for a `LocalDate` by calling `localDateWithFormatter`:

```scala mdoc:to-string
import java.time.format.DateTimeFormatter
import com.monovore.decline.time.localDateWithFormatter

val myDateArg: Argument[LocalDate] = localDateWithFormatter(
  DateTimeFormatter.ofPattern("dd/MM/yy")
)
```

In general, any date or time type should have a `xWithFormatter` method available.

## `refined` support

`decline` has support for [refined types](https://github.com/fthomas/refined) via the `decline-refined` module.
Refined types add an extra layer of safety by decorating standard types with predicates that get validated
automatically at compile time.
While command line arguments can't be validated at compile time,
refined argument types' runtime validation can still prevent
the introduction of invalid values by the user.

To make use of `decline-refined`, add the following to your `build.sbt`:

```scala
libraryDependencies += "com.monovore" %% "decline-refined" % "@DECLINE_VERSION@"
```

As an example, let's define a simple refined type and use it as a command-line argument.

```scala mdoc:to-string
import eu.timepit.refined.api.Refined
import eu.timepit.refined.numeric.Positive
import com.monovore.decline.refined._

type PosInt = Int Refined Positive

val lines = Command("lines", "Parse a positive number of lines.") {
  Opts.argument[PosInt]("count")
}
```

We can see that positive numbers will parse correctly, but anything zero or below will fail:

```scala mdoc:to-string
lines.parse(Seq("10"))
lines.parse(Seq("0"))
```

## `enumeratum` Support

> *NB: as of version 2.1 and the move to Scala 3,
> `enumeratum` support [has been dropped](https://github.com/bkirwi/decline/issues/260).
> If you're still using `enumeratum` for Scala 2,
> you may wish to stick with either an older version or reimplement...
> implementing `Argument` for `EnumEntry` is typically straightforward.*

`decline` also supports [enumeratum](https://github.com/lloydmeta/enumeratum) via the `decline-enumeratum` module.
Enumeratum provides a powerful Scala-idiomatic and Java-friendly implementation of enums.

To make use of the `enumeratum` support, add the following to your `build.sbt`:

```scala
libraryDependencies += "com.monovore" %% "decline-enumeratum" % "@DECLINE_VERSION@"
```

As an example,
we'll define a plain enumeration as required by `enumeratum`,
and use it as a command-line argument:

```scala
import _root_.enumeratum._
import com.monovore.decline.enumeratum._

sealed trait Color extends EnumEntry with EnumEntry.Lowercase

object Color extends Enum[Color] {
  case object Red extends Color
  case object Green extends Color
  case object Blue extends Color
    
  val values = findValues
}

val color = Command("color", "Return the chosen color.") {
  Opts.argument[Color]()
}
```

This parser should successfully read in `red`, `green`, or `blue`, and fail on anything else.
(NB: parsers are case sensitive!)

```scala
color.parse(Seq("red"))

color.parse(Seq("black"))

color.parse(Seq("Red"))
```

`enumeratum` also supports _value enums_, which are enumerations that are based on a value different than the actual
enum value name. Here's the same enum type as before, but backed by an integer:

```scala
import _root_.enumeratum.values._
import com.monovore.decline.enumeratum._

sealed abstract class IntColor(val value: Int) extends IntEnumEntry

object IntColor extends IntEnum[IntColor] {
  case object Red extends IntColor(0)
  case object Green extends IntColor(1)
  case object Blue extends IntColor(2)

  val values = findValues
}

val intColor = Command("int-color", "Shows the chosen color") {
  Opts.argument[IntColor]()
}
```

Value parsers expect the underlying enum value.
Our new `IntEnum` parser will fail on anything but `0`, `1`, or `2`.

```scala
intColor.parse(Seq("0"))

intColor.parse(Seq("red"))

intColor.parse(Seq("8"))
```

## Defining Your Own

In some cases, you'll want to take a command-line argument that doesn't quite map to some provided type.
Say you have the following key-value config type:

```scala mdoc:to-string
case class Config(key: String, value: String)
```

You can define an option that collects a list of configs, by specifying a
custom metavar and adding additional validation and parsing logic:

```scala mdoc:to-string
import cats.data.Validated

Opts.option[String]("config", "Specify an additional config.", metavar = "key:value")
  .mapValidated { string =>
    string.split(":", 2) match {
      case Array(key, value) => Validated.valid(Config(key, value))
      case _ => Validated.invalidNel(s"Invalid key:value pair: $string")
    }
  }
```

For most cases, this works perfectly well! For larger applications, though --
where many different options, subcommands or programs might want to use this
same basic config type -- doing this sort of thing each time is verbose and
error-prone.

It's easy enough to bundle the metavar and parsing logic together in an `Argument` instance:

```scala mdoc:to-string
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

```scala mdoc:to-string
Opts.option[Config]("config", "Specify an additional config.")
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
