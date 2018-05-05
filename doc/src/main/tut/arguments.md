---
layout: docs
title:  "Arguments"
position: 3
---

# `Argument` Types

In the [guide](/usage.html), we specified the type of an option's argument like so:

```tut:book
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

TODO

## Missing Instances

In a few cases, `decline` has intentionally _not_ defined an `Argument` instance for a particular type -- since there
are better ways to achieve the same effect. Some examples:
- `Boolean`: supporting `Boolean` arguments like `Opts.option[Boolean]("verbose", ???)` would lead to command-line usage like 
  `my-command --verbose true`... but users of other POSIX-ish command line tools would expect `my-command --verbose`
   instead. You can that more idiomatic style with `Opts.flag("verbose", ???).orFalse`; consider using that instead.
- `java.io.File`, `java.net.URL`: these types are mostly superseded by better alternatives (`java.nio.file.Path` and
  `java.net.URI`, respectively), and they support easy conversions to the older types to interoperate with existing code.
- `List[A]`: you might expect to be able to define a `Opts.option[List[String]](...)` to parse a comma-separated list of
  strings, like `--exclude foo,bar`. This ends up a little bit tricky in the general case: either you can't parse strings
  that contain commas, or you need some "escaping" mechanism, neither of which is particularly pleasant or idiomatic for
  users of a CLI. Instead, consider using the plural methods like `Opts.options` or `Opts.arguments` to accumulate a list,
  like `--exclude foo --exclude bar`. (This is also easier to use programatically!)