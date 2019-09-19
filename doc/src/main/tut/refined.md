---
layout: docs
title:  "Refined Argument types"
position: 3
---

# Refined Argument Types

Decline has support for [refined types](https://github.com/fthomas/refined) via the `decline-refined` module.
Refined types add an extra layer of safety by decorating standard types with predicates that get validated
automatically at compile time.

Whilst in the case of command line arguments such validation can not happen at compile time since the values are
not know at the moment of building the code, runtime validation can still be done right before any value of
the command arguments is passed into the application logic, preventing the introduction of invalid values by the user.

## Defining Refined Type Arguments

To make use of the refined types add the following to your `build.sbt`:

```scala
libraryDependencies += "com.monovore" %% "decline-refined" % "0.4.0"
```

And now we need to add a series of imports into our current scope:

```tut:silent
import com.monovore.decline._
import com.monovore.decline.refined._
import eu.timepit.refined.api.Refined
import eu.timepit.refined.auto._
import eu.timepit.refined.numeric.Positive
```

Following there is a very simple example of a numeric option which needs to be positive. We start by defining
a new type that can only hold positive integers:

```tut:book
type PosInt = Int Refined Positive
```

And now we define an option parameterised on that specific type and a command so we can test it:

```tut:book
val lines = Opts.option[PosInt]("lines", short = "n", metavar = "count", help = "Set a number of lines.")
val cmd = Command("echoLines", "Echoes the number of lines")(lines)
```

If we now try to parse a command line in which we have a `--lines 10` argument pair we should be able to parse
the `lines` option:

```tut:book
cmd.parse(Seq("--lines", "10"))
```

However if we pass a 0 or a non positive value, the `parse` operation should fail:

```tut:book
cmd.parse(Seq("--lines", "0"))
```

Check the documentation for [refined](https://github.com/fthomas/refined) to see how you can define your own
refined types.
