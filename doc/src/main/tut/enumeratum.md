---
layout: docs
title:  "Enumeratum Support"
position: 3
---

# Enumeratum Support

Enums support based on [enumeratum](https://github.com/lloydmeta/enumeratum) is provided via the `decline-enumeratum` module.
Enumeratum provides a powerful Scala-idiomatic and Java-friendly implementation of enums.

This module allows to use the names (or values) associated with the different cases of an enum as an option argument in the command line.

## Defining enumerated values

To make use of the enumeratum features add the following to your `build.sbt`:

```scala
libraryDependencies += "com.monovore" %% "decline-enumeratum" % "0.7.0"
```

And now we need to add a series of imports into our current scope:

```tut:silent
import enumeratum._

import com.monovore.decline.{Opts, Command}
import com.monovore.decline.enumeratum._
```

Following there is a very simple example of a plain enum being used as a command line option. First of all,
we define the given enumeration as per required by `enumeratum`:

```tut:book
object simple {
  sealed trait Color extends EnumEntry with EnumEntry.Lowercase
  object Color extends Enum[Color] {
    case object Red extends Color
    case object Green extends Color
    case object Blue extends Color

    val values = findValues
  }
}

import simple._
```

_Note that the `with EnumEntry.Lowercase` is not really needed, but it will make the different cases available as
lowercase string values._

And now we define an option parameterised on that enum type and a command so we can test it:

```tut:book
val color = Opts.option[Color]("color", short = "c", metavar = "color", help = "Choose a color.")
val cmd = Command("showColor", "Shows the chosen color")(color)
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