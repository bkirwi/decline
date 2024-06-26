---
layout: docs
title:  "Structuring a complex CLI"
position: 4
---

If you've read the [user's guide](./usage.html),
you've seen how to use `decline` to write a small command-line application.
Writing a large one is, broadly speaking, just more of the same...
but like any large codebase,
a big CLI can grow hard to deal with if it's not structured well.

`decline` is shaped a little differently than other command-line parsers,
so the options you have for structuring your code might not be obvious!
In this page,
we'll walk through a few ideas that can help
and when they may be useful.

## A running example

Let's suppose we're working on a simple command-line application.
It fetches its input from a remote endpoint or a local file,
processes it,
and writes out the result to another file.

```scala mdoc:to-string
import com.monovore.decline._
import cats.syntax.all._

import java.net.URI
import scala.concurrent.duration.Duration
import java.nio.file.Path

// We'll start by defining our individual options...
val uriOpt = Opts.option[URI]("input-uri", "Location of the remote file.")
val timeoutOpt = 
    Opts.option[Duration]("timeout", "Timeout for fetching the remote file.")
      .withDefault(Duration.Inf)
val fileOpt = Opts.option[Path]("input-file", "Local path to input file.")
val outputOpt = Opts.argument[Path]("output-file")

// ...along with a case class that captures all our configuration data.
case class Config(
    uri: Option[URI],
    timeout: Duration,
    file: Option[Path],
    output: Path,
)

// Then, we combine our individual options into a `Opts[Config]` and validate the result.
val configOpts: Opts[Config] =
  (uriOpt.orNone, timeoutOpt, fileOpt.orNone, outputOpt)
    .mapN(Config.apply)
    .validate("remote uri must be https")(_.uri.forall(_.getScheme == "https"))
    .validate("timeout option is only valid for remote files")(c => 
      // if a non-default timeout is specified, uri must be present
      c.timeout != Duration.Inf || c.uri.isDefined
    )
    .validate("must provide either uri or file")(c => c.uri.isDefined ^ c.file.isDefined)

// And finally, we pass the validated config to a `run` function that does the real work.
def runApp(config: Config) = ???
configOpts.map(runApp)
```

To be clear: this code is basically fine!
It will run without errors,
and it's fairly easy to understand what it does.
The suggestions below are most valuable
for complex interfaces with multiple options or subcommands...
but they're easier to explain with a simple example.

## Early validation

Our example code first builds the full config,
then validates it,
and finally passes it to the rest of the program.
We don't actually need the full config
to check that our input URI is valid, though;
let's move that validation up to right where the option is defined.

```scala mdoc:nest:to-string
val uriOpt =
  Opts.option[URI]("uri", "Location of the remote file.")
    .validate("remote uri must be https")(_.getScheme == "https")
```

You might prefer this because:
- The validation itself is simpler;
it doesn't need to extract the URI from the config object, 
or handle the case where it doesn't exist.
- Since the option definition and the validation live together,
it's a bit easier to see that the URI is validated correctly,
and harder to accidentally pass an unvalidated URI around by mistake.

A fairly minor improvement in this case...
but the more complex your CLI, the more this sort of thing can help.

## Grouping related options

The initial example listed out all the options in a single case class.
As your program grows,
it's often helpful to break out smaller groups of options
that get passed around and validated together.
In our example,
the timeout only really makes sense if we're fetching a remote resource,
so let's group those two together in a new case class.

```scala mdoc:nest:to-string
case class RemoteConfig(uri: URI, timeout: Duration)

val remoteOpts = (uriOpt, timeoutOpt).mapN(RemoteConfig.apply)

case class Config(
    remote: Option[RemoteConfig],
    file: Option[Path],
    output: Path,
)

val configOpts = 
  (remoteOpts.orNone, fileOpt.orNone, outputOpt)
    .mapN(Config.apply)
    .validate("must provide either uri or file")(c => c.remote.isDefined ^ c.file.isDefined)
```

With this change,
it's no longer possible to define a `Config` that has a `timeout` but not a `uri`.
This means we can get rid of one of our explicit validations;
`decline` will do the equivalent input check automatically,
and its autogenerated `Usage: ` info in the help output
will also reflect the new structure.

Other benefits include:
- A single case class with twenty fields can get pretty unwieldy;
  it becomes easy to accidentally pass arguments in the wrong order, for example.
  Grouping arguments makes these errors less likely,
  both because small groups are easier to see at a glance
  and because the more explicit and specific your types are 
  the more the compiler can help catch mistakes.
- It can make it easier to validate early --
  if we decided we wanted to ban custom timeouts
  when the URI was `localhost`, for example,
  we could add that validation to `remoteOpts` instead of `configOpts`.

## Mutual exclusion

`orElse` is often used for subcommands,
but it works just as well for ordinary options.
Since our input file is always either local or remote,
using `orElse` can let us express that more directly.

```scala mdoc:nest:to-string
// Either would for two mutually-exclusive possibilities,
// but a sealed trait is a bit more general.
sealed trait InputConfig
case class RemoteConfig(uri: URI, timeout: Duration) extends InputConfig
case class LocalConfig(file: Path) extends InputConfig

val remoteOpts = (uriOpt, timeoutOpt).mapN(RemoteConfig.apply)
val localOpts = fileOpt.map(LocalConfig.apply)
val inputOpts = remoteOpts orElse localOpts

case class Config(
    input: InputConfig,
    queries: Path,
)

val configOpts = (inputOpts, outputOpt).mapN(Config.apply)
```

That gets rid of our final config validation;
`decline` ensures that the user passes either `--uri` or `--input-file`,
but never both.

It's often possible to replace ad-hoc validation
with more precise data modelling like this,
and it's almost always a good idea when you can:
it simplifies the code,
improves the error messages and usage texts that `decline` generates,
and helps "make illegal states unrepresentable" in your program.

## Config and effect style

Through every step of the refactoring above,
the shape of our command-line parser closely matches the `Config` datastructure;
we build parsers for case classes using `mapN` and sealed traits using `orElse`,
working our way up from simpler types to the full `Config`.
(And once that's done,
we hand the whole thing off to some `run` function
that does the actual work.)
This "config" pattern is a very common way to structure an application using `decline`,
and it's easy to test:
users often write unit tests that pass different arguments to the parser
and assert that they parse successfully,
or have the contents you'd expect.

It's also possible to avoid building up intermediate configs,
instead just calling functions that take the appropriate action directly.

```scala mdoc:nest:to-string
// This example code uses cats-effect's IO, but the pattern works just as well for
// imperative programs... just change the return type of the fetch functions
// to `Future[String]`, `String`, or whatever else makes sense in your context.
import cats.effect.IO

def fetchRemote(uri: URI, timeout: Duration): IO[String] = ???
def fetchLocal(file: Path): IO[String] = ???

val remoteOpts = (uriOpt, timeoutOpt).mapN(fetchRemote)
val localOpts = fileOpt.map(fetchLocal)
val inputOpts = remoteOpts orElse localOpts

def run(input: IO[String], output: Path): IO[Unit] = ???

val configOpts = (inputOpts, outputOpt).mapN(run)
```

This code is shorter and more direct than the config-based example,
and avoids needing any intermediate data structures to represent the input.
On the other hand,
it's somewhat more opaque and harder to test or validate.
Either approach can work well,
and some apps use both styles in different places.
Follow your heart!
