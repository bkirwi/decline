---
layout: docs
title:  "Scala.js and Scala Native"
position: 6
---

# Scala.js and Scala Native

As of version `0.3.0`, `decline` is available for Scala.js!

Everything that works on the JVM should work in JavaScript as well,
including everything in the [main guide](./usage.html).
(If you find something that doesn't, please [open an issue][new-issue]!)
This document has a few more details on the nuts and bolts
of getting a command-line application up and running.

[new-issue]: https://github.com/bkirwi/decline/issues/new

## Working with CommandApp

If you're using a command-line parsing library like `decline`,
you're probably writing a command-line application...
and these work quite differently on the JVM and in JavaScript.
In particular,
JavaScript environments have no concept of a "`main` method" --
runtimes like Node.js provide their own interfaces for accessing command-line arguments
for applications that need them.

[`decline`'s `CommandApp` abstracts over these differences.][defining-an-application]
If you define an application using that style...

```scala mdoc:to-string
import com.monovore.decline._

object MyApp extends CommandApp(
  name = "my-app",
  header = "This compiles to JavaScript!",
  main = {
    val loudOpt = Opts.flag("loud", "Do something noisy!").orFalse
    
    for (loud <- loudOpt) yield {
      if (loud) println("HELLO WORLD!")
      else println("hello world!")
    }
  }
)
```

...and compile it to JS, you should be able to kick it off with:
 
```bash
$ node my-compiled-app.js --loud
HELLO WORLD!
```

This makes it possible to write a single command-line application
and compile it for both Node and the JVM!

If you haven't written a CLI app with Scala.js before, some things to remember:

  - Make sure you configure Scala.js to [compile your code as an application][building].
  - The standard SBT `run` command won't forward the arguments along;
    you'll need to build the JavaScript file and then invoke it manually with `node`.
  
## Ambient Arguments

If you'd rather not use `CommandApp` to set up a main method for you,
it's still possible to use `decline` as a library.
However, it gets a little tricky to get ahold of the command-line arguments --
when Scala.js calls your main method with an `Array[String]`,
the array is always empty!

As a workaround,
`decline` wraps [Node.js' `process.argv` interface][process.argv]
for platforms where that is available:

```scala mdoc:to-string
import com.monovore.decline.PlatformApp

PlatformApp.ambientArgs match {
  case None => // No arguments available! (JVM / Browser)
  case Some(args) => // Found 'em! (Node.js)
}
```

Of course, this means your code has to handle running under the JVM or Node.js differently,
which makes things more complicated than the `CommandApp` style above.

# Scala Native

`decline` also publishes artifacts for Scala Native.

At time of writing,
a bug in Scala Native prevents `CommandApp` and `CommandIOApp` from working correctly.
As a workaround,
you can implement your own main method as normal
and call `command.parse(...)` on your `Command` instance explicitly.

[defining-an-application]: ./usage.html#defining-an-application
[building]: https://www.scala-js.org/doc/project/building.html#actually-do-something
[process.argv]: https://nodejs.org/api/process.html#process_process_argv