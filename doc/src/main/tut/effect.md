---
layout: docs
title:  "Integration with Cats Effect"
position: 4
---

# Integration with Cats Effect

For those interested in pure functional programming the module `decline-effect` provides with a thin integration with
`cats-effect`, allowing users to parse the command line into a data structure that can be interpreted into an
`IO[ExitCode]`. The usage is very similar to what is specified in [`decline`'s user guide][defining-an-application]
but instead of using the `CommandApp`, we are going to use a newly defined `CommandIOApp`.

In the following lines we are going to show how to do this by following an example.

## Building an `IO`-based application

We are going to create a command line interface like the one used by Docker when used from the terminal because is a
very well known one and has a mix of options, flags, arguments and subcommands. Of course, this is just going to be
a partial implementation -we will focus only on the `ps` and `build` commands- just what is needed to get the point across.

To start with, need to add the module to our dependencies:

```scala
libraryDependencies += "com.monovore" %% "decline-effect" % "0.6.3"
```

And add the necessary imports:

```tut:silent
import cats.effect._
import cats.implicits._

import com.monovore.decline._
import com.monovore.decline.effect._
```

### Defining the command line interface

Let's now define our interface as a data type, and let's simplify it a lot, we are going to only support one option
(`-a`) for the `ps` command and for the `build` one we require a path and optionally a name for the `Dockerfile`. If
we were looking at the help text for each of these commands it would like the following:

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

So when translating that interface into data types we have something like the following:

```tut:book
case class ShowProcesses(all: Boolean)
case class BuildImage(dockerFile: Option[String], path: String)
```

Now we need to build our parser composing the individual elements for each of the components. So the `ps` subcommand
will look like the following:

```tut:book
val showProcessesOpts: Opts[ShowProcesses] = Opts.subcommand("ps", "Lists docker processes running!") {
  Opts.flag("all", "Whether to show all running processes.", short = "a")
    .orFalse
    .map(ShowProcesses)
}
```

And the `build` command would be as follows:

```tut:book
val dockerFileOpts: Opts[Option[String]] = Opts.option[String](
  "file", "The name of the Dockerfile.", short = "f"
).orNone

val pathOpts: Opts[String] = Opts.argument[String](metavar = "path")

val buildOpts: Opts[BuildImage] = Opts.subcommand("build", "Builds a docker image!") {
  (dockerFileOpts, pathOpts).mapN(BuildImage)
}
```

### Interpreting our command line interface

Now we just need to use the previous defined interface building an interpreter for the data type we just created. This
is done using the `CommandIOApp` as follows:

```tut:book
object DockerApp extends CommandIOApp(
  name = "docker",
  header = "Faux docker command line",
  version = "0.0.x"
) {

  override def main: Opts[IO[ExitCode]] = {
    val showProcessesCmd = showProcessesOpts.map(showProcesses)
    val buildCmd = buildOpts.map(buildImage)

    showProcessesCmd orElse buildCmd
  }

  private def showProcesses(cmd: ShowProcesses): IO[ExitCode] = ???
  
  private def buildImage(cmd: BuildImage): IO[ExitCode] = ???

}
```

The `main: Opts[IO[ExitCode]]` is what aggregates all the bits and pieces of our command line interpreter, this consists of the
previously defined subcommand options _mapped_ into `IO` actions that correspond to the given command line arguments. This has to be
implemented inside the `CommandIOApp` since it provides with a _runtime_ for running those `IO` actions.

[defining-an-application]: ./usage.html#defining-an-application