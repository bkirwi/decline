---
layout: docs
title:  "Cats Effect"
position: 4
---

# Integration with Cats Effect

[Cats Effect](https://typelevel.org/cats-effect/) is a library 
for writing side-effectful programs in a pure functional style.

The module `decline-effect` defines a thin integration between `decline` and Cats Effect.
In particular, `CommandIOApp` combines
the simple and rich CLI from [`decline`'s `CommandApp`](./usage.html#defining-an-application)
and the pure effect management of [`cats-effect`'s `IOApp`](https://typelevel.org/cats-effect/datatypes/ioapp.html)

but instead of using the `CommandApp`, we are going to use a newly defined `CommandIOApp`.

In the following lines we are going to show how to do this by following an example.

## Building an `IO`-based application

As an example, we'll reimplement a small Docker-like command-line interface:
it's fairly well known CLI tool, and has a nice mix of options, flags, arguments and subcommands.
We'll focus only on the `ps` and `build` commands -- just enough to get the point across.

First, we'll add the module to our dependencies:

```scala
libraryDependencies += "com.monovore" %% "decline-effect" % "0.6.3"
```

And add the necessary imports:

```scala mdoc:to-string
import cats.effect._
import cats.implicits._

import com.monovore.decline._
import com.monovore.decline.effect._
```

### Defining the command line interface

Let's now define our interface as a data type.
We're aiming for the following very-simplified Docker-like interface:

```bash
$ docker ps --help
Usage: docker ps [--all]

Lists docker processes running!

    --all
            Whether to show all running processes.
    --help
            Display this help text.
```

```bash
$ docker build --help
Usage: docker build [--file <name>] path

Builds a docker image!

    --file <name>
            The name of the Dockerfile.
    --help
            Display this help text.
```

If we're translating that interface into data types, we'll end up with something like the following:

```scala mdoc:to-string
case class ShowProcesses(all: Boolean)
case class BuildImage(dockerFile: Option[String], path: String)
```

Now we'll build our parser, composing the individual elements for each of the components.
Here's the `ps` subcommand:

```scala mdoc:to-string
val showProcessesOpts: Opts[ShowProcesses] =
  Opts.subcommand("ps", "Lists docker processes running!") {
    Opts.flag("all", "Whether to show all running processes.", short = "a")
      .orFalse
      .map(ShowProcesses)
  }
```

And the `build` command would be as follows:

```scala mdoc:to-string
val dockerFileOpts: Opts[Option[String]] =
  Opts.option[String]( "file", "The name of the Dockerfile.", short = "f" ).orNone

val pathOpts: Opts[String] =
  Opts.argument[String](metavar = "path")

val buildOpts: Opts[BuildImage] =
  Opts.subcommand("build", "Builds a docker image!") {
    (dockerFileOpts, pathOpts).mapN(BuildImage)
  }
```

### Interpreting our command line interface

Now we'll build an interpreter for the data type we just created.
This could be done using the `CommandIOApp` as follows:

```scala mdoc:to-string
object DockerApp extends CommandIOApp(
  name = "docker",
  header = "Faux docker command line",
  version = "0.0.x"
) {

  override def main: Opts[IO[ExitCode]] =
    (showProcessesOpts orElse buildOpts).map {
      case ShowProcesses(all) => ???
      case BuildImage(dockerFile, path) => ???
    }
}
```

The `main: Opts[IO[ExitCode]]` is what aggregates all the bits and pieces of our command line interpreter.
In this case, we just take the previously-defined subcommand options,
and map into `IO` actions that correspond to the given command line arguments.
(It's usually handy to define these actions within the `CommandIOApp` itself...
it puts a `ContextShift` and `Timer` in implicit scope,
which are required by lots of other code in the Cats Effect ecosystem.)