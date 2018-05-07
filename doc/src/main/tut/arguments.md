---
layout: docs
title:  "Arguments"
position: 3
---

# `Argument` Types

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
`decline` provides instances for many commonly used standard-library types: strings, numbers, paths, URIs, and so on.

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