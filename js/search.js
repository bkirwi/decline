// When the user clicks on the search box, we want to toggle the search dropdown
function displayToggleSearch(e) {
  e.preventDefault();
  e.stopPropagation();

  closeDropdownSearch(e);
  
  if (idx === null) {
    console.log("Building search index...");
    prepareIdxAndDocMap();
    console.log("Search index built.");
  }
  const dropdown = document.querySelector("#search-dropdown-content");
  if (dropdown) {
    if (!dropdown.classList.contains("show")) {
      dropdown.classList.add("show");
    }
    document.addEventListener("click", closeDropdownSearch);
    document.addEventListener("keydown", searchOnKeyDown);
    document.addEventListener("keyup", searchOnKeyUp);
  }
}

//We want to prepare the index only after clicking the search bar
var idx = null
const docMap = new Map()

function prepareIdxAndDocMap() {
  const docs = [  
    {
      "title": "Arguments",
      "url": "/decline/arguments.html",
      "content": "Argument Types In the guide, we specified the type of an option’s argument like so: import com.monovore.decline._ import java.nio.file.Path val path = Opts.option[Path](\"input\", \"Path to the input file.\") // path: Opts[Path] = Opts(--input &lt;path&gt;) This does two different things for us: It specifies a parsing function – when the user passes a string as an argument, decline will try and interpret it as a path and report an error if it can’t. It specifies a default ‘metavar’ – the &lt;path&gt; text you can see in the output above. This helps the user understand what sort of input your program expects in that position. This information is provided by the com.monovore.decline.Argument type class. decline provides instances for many commonly used standard-library types: strings, numbers, paths, URIs… java.time support decline has built-in support for the java.time library introduced in Java 8, including argument instances for Duration, ZonedDateTime, ZoneId, Instant, and others. You’ll need to pull these in with an explicit import: import java.time._ import com.monovore.decline.time._ val fromDate = Opts.option[LocalDate](\"fromDate\", help = \"Local date from where start looking at data\") // fromDate: Opts[LocalDate] = Opts(--fromDate &lt;iso-local-date&gt;) val timeout = Opts.option[Duration](\"timeout\", help = \"Operation timeout\") // timeout: Opts[Duration] = Opts(--timeout &lt;iso-duration&gt;) By default, this parses using the standard ISO 8601 formats. If you’d like to use a custom time format, decline also provides Argument builders that take a java.time.format.DateTimeFormatter. For example, you can define a custom parse for a LocalDate by calling localDateWithFormatter: import java.time.format.DateTimeFormatter import com.monovore.decline.time.localDateWithFormatter val myDateArg: Argument[LocalDate] = localDateWithFormatter( DateTimeFormatter.ofPattern(\"dd/MM/yy\") ) // myDateArg: Argument[LocalDate] = Argument(&lt;local-date&gt;) In general, any date or time type should have a xWithFormatter method available. refined support decline has support for refined types via the decline-refined module. Refined types add an extra layer of safety by decorating standard types with predicates that get validated automatically at compile time. While command line arguments can’t be validated at compile time, refined argument types’ runtime validation can still prevent the introduction of invalid values by the user. To make use of decline-refined, add the following to your build.sbt: libraryDependencies += \"com.monovore\" %% \"decline-refined\" % \"2.4.1\" As an example, let’s define a simple refined type and use it as a command-line argument. import eu.timepit.refined.api.Refined import eu.timepit.refined.numeric.Positive import com.monovore.decline.refined._ type PosInt = Int Refined Positive val lines = Command(\"lines\", \"Parse a positive number of lines.\") { Opts.argument[PosInt](\"count\") } // lines: Command[PosInt] = com.monovore.decline.Command@112e43cf We can see that positive numbers will parse correctly, but anything zero or below will fail: lines.parse(Seq(\"10\")) // res0: Either[Help, PosInt] = Right(10) lines.parse(Seq(\"0\")) // res1: Either[Help, PosInt] = Left(Predicate failed: (0 &gt; 0). // // Usage: lines &lt;count&gt; // // Parse a positive number of lines. // // Options and flags: // --help // Display this help text.) enumeratum Support NB: as of version 2.1 and the move to Scala 3, enumeratum support has been dropped. If you’re still using enumeratum for Scala 2, you may wish to stick with either an older version or reimplement… implementing Argument for EnumEntry is typically straightforward. decline also supports enumeratum via the decline-enumeratum module. Enumeratum provides a powerful Scala-idiomatic and Java-friendly implementation of enums. To make use of the enumeratum support, add the following to your build.sbt: libraryDependencies += \"com.monovore\" %% \"decline-enumeratum\" % \"2.4.1\" As an example, we’ll define a plain enumeration as required by enumeratum, and use it as a command-line argument: import _root_.enumeratum._ import com.monovore.decline.enumeratum._ sealed trait Color extends EnumEntry with EnumEntry.Lowercase object Color extends Enum[Color] { case object Red extends Color case object Green extends Color case object Blue extends Color val values = findValues } val color = Command(\"color\", \"Return the chosen color.\") { Opts.argument[Color]() } This parser should successfully read in red, green, or blue, and fail on anything else. (NB: parsers are case sensitive!) color.parse(Seq(\"red\")) color.parse(Seq(\"black\")) color.parse(Seq(\"Red\")) enumeratum also supports value enums, which are enumerations that are based on a value different than the actual enum value name. Here’s the same enum type as before, but backed by an integer: import _root_.enumeratum.values._ import com.monovore.decline.enumeratum._ sealed abstract class IntColor(val value: Int) extends IntEnumEntry object IntColor extends IntEnum[IntColor] { case object Red extends IntColor(0) case object Green extends IntColor(1) case object Blue extends IntColor(2) val values = findValues } val intColor = Command(\"int-color\", \"Shows the chosen color\") { Opts.argument[IntColor]() } Value parsers expect the underlying enum value. Our new IntEnum parser will fail on anything but 0, 1, or 2. intColor.parse(Seq(\"0\")) intColor.parse(Seq(\"red\")) intColor.parse(Seq(\"8\")) Defining Your Own In some cases, you’ll want to take a command-line argument that doesn’t quite map to some provided type. Say you have the following key-value config type: case class Config(key: String, value: String) You can define an option that collects a list of configs, by specifying a custom metavar and adding additional validation and parsing logic: import cats.data.Validated Opts.option[String](\"config\", \"Specify an additional config.\", metavar = \"key:value\") .mapValidated { string =&gt; string.split(\":\", 2) match { case Array(key, value) =&gt; Validated.valid(Config(key, value)) case _ =&gt; Validated.invalidNel(s\"Invalid key:value pair: $string\") } } // res2: Opts[Config] = Opts(--config &lt;key:value&gt;) For most cases, this works perfectly well! For larger applications, though – where many different options, subcommands or programs might want to use this same basic config type – doing this sort of thing each time is verbose and error-prone. It’s easy enough to bundle the metavar and parsing logic together in an Argument instance: implicit val configArgument: Argument[Config] = new Argument[Config] { def read(string: String) = { string.split(\":\", 2) match { case Array(key, value) =&gt; Validated.valid(Config(key, value)) case _ =&gt; Validated.invalidNel(s\"Invalid key:value pair: $string\") } } def defaultMetavar = \"key:value\" } // configArgument: Argument[Config] = Argument(&lt;key:value&gt;) …and then defining new options that take configs becomes trivial: Opts.option[Config](\"config\", \"Specify an additional config.\") // res3: Opts[Config] = Opts(--config &lt;key:value&gt;) Missing Instances In a few cases, decline has intentionally not defined an Argument instance for a particular type – since there are better ways to achieve the same effect. Some examples: Boolean: supporting Boolean arguments like Opts.option[Boolean](\"verbose\", ???) would lead to command-line usage like my-command --verbose true… but users of other POSIX-ish command line tools would expect my-command --verbose. You can get that more idiomatic style with Opts.flag(\"verbose\", ???).orFalse; consider using that instead! java.io.File, java.net.URL: these types are mostly superseded by better alternatives (java.nio.file.Path and java.net.URI, respectively), and they support easy conversions to the older types to interoperate with existing code. List[A]: you might expect to be able to define a Opts.option[List[String]](...) to parse a comma-separated list of strings, like --exclude foo,bar. This ends up a little bit tricky in the general case: either you can’t parse strings that contain commas, or you need some “escaping” mechanism, neither of which is particularly pleasant or idiomatic for users. Instead, consider using the plural methods like Opts.options or Opts.arguments to accumulate a list, like --exclude foo --exclude bar. (This is also easier to use programatically!)"
    } ,    
    {
      "title": "Cats Effect",
      "url": "/decline/effect.html",
      "content": "Integration with Cats Effect Cats Effect is a library for writing side-effectful programs in a pure functional style. The module decline-effect defines a thin integration between decline and Cats Effect. In particular, CommandIOApp combines the simple and rich CLI from decline’s CommandApp and the pure effect management of cats-effect’s IOApp but instead of using the CommandApp, we are going to use a newly defined CommandIOApp. In the following lines we are going to show how to do this by following an example. Building an IO-based application As an example, we’ll reimplement a small Docker-like command-line interface: it’s fairly well known CLI tool, and has a nice mix of options, flags, arguments and subcommands. We’ll focus only on the ps and build commands – just enough to get the point across. First, we’ll add the module to our dependencies: libraryDependencies += \"com.monovore\" %% \"decline-effect\" % \"2.4.1\" And add the necessary imports: import cats.effect._ import cats.implicits._ import com.monovore.decline._ import com.monovore.decline.effect._ Defining the command line interface Let’s now define our interface as a data type. We’re aiming for the following very-simplified Docker-like interface: $ docker ps --help Usage: docker ps [--all] Lists docker processes running! --all Whether to show all running processes. --help Display this help text. $ docker build --help Usage: docker build [--file &lt;name&gt;] path Builds a docker image! --file &lt;name&gt; The name of the Dockerfile. --help Display this help text. If we’re translating that interface into data types, we’ll end up with something like the following: case class ShowProcesses(all: Boolean) case class BuildImage(dockerFile: Option[String], path: String) Now we’ll build our parser, composing the individual elements for each of the components. Here’s the ps subcommand: val showProcessesOpts: Opts[ShowProcesses] = Opts.subcommand(\"ps\", \"Lists docker processes running!\") { Opts.flag(\"all\", \"Whether to show all running processes.\", short = \"a\") .orFalse .map(ShowProcesses) } // showProcessesOpts: Opts[ShowProcesses] = Opts(ps) And the build command would be as follows: val dockerFileOpts: Opts[Option[String]] = Opts.option[String]( \"file\", \"The name of the Dockerfile.\", short = \"f\" ).orNone // dockerFileOpts: Opts[Option[String]] = Opts([--file &lt;string&gt;]) val pathOpts: Opts[String] = Opts.argument[String](metavar = \"path\") // pathOpts: Opts[String] = Opts(&lt;path&gt;) val buildOpts: Opts[BuildImage] = Opts.subcommand(\"build\", \"Builds a docker image!\") { (dockerFileOpts, pathOpts).mapN(BuildImage) } // buildOpts: Opts[BuildImage] = Opts(build) Interpreting our command line interface Now we’ll build an interpreter for the data type we just created. This could be done using the CommandIOApp as follows: object DockerApp extends CommandIOApp( name = \"docker\", header = \"Faux docker command line\", version = \"0.0.x\" ) { override def main: Opts[IO[ExitCode]] = (showProcessesOpts orElse buildOpts).map { case ShowProcesses(all) =&gt; ??? case BuildImage(dockerFile, path) =&gt; ??? } } The main: Opts[IO[ExitCode]] is what aggregates all the bits and pieces of our command line interpreter. In this case, we just take the previously-defined subcommand options, and map into IO actions that correspond to the given command line arguments. (It’s usually handy to define these actions within the CommandIOApp itself… it puts a ContextShift and Timer in implicit scope, which are required by lots of other code in the Cats Effect ecosystem.)"
    } ,    
    {
      "title": "Home",
      "url": "/decline/",
      "content": "decline is a composable command-line parsing library, inspired by optparse-applicative and built on cats. Why decline? Full-featured: decline supports the standard set of Unix command-line idioms, including flags, options, positional arguments, and subcommands. Support for mutually-exclusive options and custom validations make it easy to mold your CLI to the shape of your application. Helpful: decline automatically generates comprehensive and precise error messages and usage texts. Functional: decline provides an immutable and functional API, usable whether or not your program is written in a functional style. Quick Start First, pull the library into your build. For sbt: // NB: 1.2.0 is the last release to support Scala 2.11 libraryDependencies += \"com.monovore\" %% \"decline\" % \"2.4.1\" Then, write a program: import cats.implicits._ import com.monovore.decline._ object HelloWorld extends CommandApp( name = \"hello-world\", header = \"Says hello!\", main = { val userOpt = Opts.option[String](\"target\", help = \"Person to greet.\").withDefault(\"world\") val quietOpt = Opts.flag(\"quiet\", help = \"Whether to be quiet.\").orFalse (userOpt, quietOpt).mapN { (user, quiet) =&gt; if (quiet) println(\"...\") else println(s\"Hello $user!\") } } ) Then, run it: $ hello-world --help Usage: hello-world [--target &lt;string&gt;] [--quiet] Says hello! Options and flags: --help Display this help text. --target &lt;string&gt; Person to greet. --quiet Whether to be quiet. $ hello-world --target friend Hello, friend! (For a more in-depth introduction, see the user’s guide!)"
    } ,      
    {
      "title": "Scala.js and Scala Native",
      "url": "/decline/scalajs.html",
      "content": "Scala.js and Scala Native As of version 0.3.0, decline is available for Scala.js! Everything that works on the JVM should work in JavaScript as well, including everything in the main guide. (If you find something that doesn’t, please open an issue!) This document has a few more details on the nuts and bolts of getting a command-line application up and running. Working with CommandApp If you’re using a command-line parsing library like decline, you’re probably writing a command-line application… and these work quite differently on the JVM and in JavaScript. In particular, JavaScript environments have no concept of a “main method” – runtimes like Node.js provide their own interfaces for accessing command-line arguments for applications that need them. decline’s CommandApp abstracts over these differences. If you define an application using that style… import com.monovore.decline._ object MyApp extends CommandApp( name = \"my-app\", header = \"This compiles to JavaScript!\", main = { val loudOpt = Opts.flag(\"loud\", \"Do something noisy!\").orFalse for (loud &lt;- loudOpt) yield { if (loud) println(\"HELLO WORLD!\") else println(\"hello world!\") } } ) …and compile it to JS, you should be able to kick it off with: $ node my-compiled-app.js --loud HELLO WORLD! This makes it possible to write a single command-line application and compile it for both Node and the JVM! If you haven’t written a CLI app with Scala.js before, some things to remember: Make sure you configure Scala.js to compile your code as an application. The standard SBT run command won’t forward the arguments along; you’ll need to build the JavaScript file and then invoke it manually with node. Ambient Arguments If you’d rather not use CommandApp to set up a main method for you, it’s still possible to use decline as a library. However, it gets a little tricky to get ahold of the command-line arguments – when Scala.js calls your main method with an Array[String], the array is always empty! As a workaround, decline wraps Node.js’ process.argv interface for platforms where that is available: import com.monovore.decline.PlatformApp PlatformApp.ambientArgs match { case None =&gt; // No arguments available! (JVM / Browser) case Some(args) =&gt; // Found 'em! (Node.js) } Of course, this means your code has to handle running under the JVM or Node.js differently, which makes things more complicated than the CommandApp style above. Scala Native decline also publishes artifacts for Scala Native. Older versions of Scala Native have a bug that prevents CommandApp and CommandIOApp from working correctly. Make sure you’re using Scala Native 0.4.4 or later!"
    } ,      
    {
      "title": "Structuring a complex CLI",
      "url": "/decline/structure.html",
      "content": "If you’ve read the user’s guide, you’ve seen how to use decline to write a small command-line application. Writing a large one is, broadly speaking, just more of the same… but like any large codebase, a big CLI can grow hard to deal with if it’s not structured well. decline is shaped a little differently than other command-line parsers, so the options you have for structuring your code might not be obvious! In this page, we’ll walk through a few ideas that can help and when they may be useful. A running example Let’s suppose we’re working on a simple command-line application. It fetches its input from a remote endpoint or a local file, processes it, and writes out the result to another file. import com.monovore.decline._ import cats.implicits._ import java.net.URI import scala.concurrent.duration.Duration import java.nio.file.Path // We'll start by defining our individual options... val uriOpt = Opts.option[URI](\"input-uri\", \"Location of the remote file.\") // uriOpt: Opts[URI] = Opts(--input-uri &lt;uri&gt;) val timeoutOpt = Opts.option[Duration](\"timeout\", \"Timeout for fetching the remote file.\") .withDefault(Duration.Inf) // timeoutOpt: Opts[Duration] = Opts([--timeout &lt;duration&gt;]) val fileOpt = Opts.option[Path](\"input-file\", \"Local path to input file.\") // fileOpt: Opts[Path] = Opts(--input-file &lt;path&gt;) val outputOpt = Opts.argument[Path](\"output-file\") // outputOpt: Opts[Path] = Opts(&lt;output-file&gt;) // ...along with a case class that captures all our configuration data. case class Config( uri: Option[URI], timeout: Duration, file: Option[Path], output: Path, ) // Then, we combine our individual options into a `Opts[Config]` and validate the result. val configOpts: Opts[Config] = (uriOpt.orNone, timeoutOpt, fileOpt.orNone, outputOpt) .mapN(Config.apply) .validate(\"remote uri must be https\")(_.uri.forall(_.getScheme == \"https\")) .validate(\"timeout option is only valid for remote files\")(c =&gt; // if a non-default timeout is specified, uri must be present c.timeout != Duration.Inf || c.uri.isDefined ) .validate(\"must provide either uri or file\")(c =&gt; c.uri.isDefined ^ c.file.isDefined) // configOpts: Opts[Config] = Opts([--input-uri &lt;uri&gt;] [--timeout &lt;duration&gt;] [--input-file &lt;path&gt;] &lt;output-file&gt;) // And finally, we pass the validated config to a `run` function that does the real work. def runApp(config: Config) = ??? configOpts.map(runApp) // res0: Opts[Nothing] = Opts([--input-uri &lt;uri&gt;] [--timeout &lt;duration&gt;] [--input-file &lt;path&gt;] &lt;output-file&gt;) To be clear: this code is basically fine! It will run without errors, and it’s fairly easy to understand what it does. The suggestions below are most valuable for complex interfaces with multiple options or subcommands… but they’re easier to explain with a simple example. Early validation Our example code first builds the full config, then validates it, and finally passes it to the rest of the program. We don’t actually need the full config to check that our input URI is valid, though; let’s move that validation up to right where the option is defined. val uriOpt = Opts.option[URI](\"uri\", \"Location of the remote file.\") .validate(\"remote uri must be https\")(_.getScheme == \"https\") // uriOpt: Opts[URI] = Opts(--uri &lt;uri&gt;) You might prefer this because: The validation itself is simpler; it doesn’t need to extract the URI from the config object, or handle the case where it doesn’t exist. Since the option definition and the validation live together, it’s a bit easier to see that the URI is validated correctly, and harder to accidentally pass an unvalidated URI around by mistake. A fairly minor improvement in this case… but the more complex your CLI, the more this sort of thing can help. Grouping related options The initial example listed out all the options in a single case class. As your program grows, it’s often helpful to break out smaller groups of options that get passed around and validated together. In our example, the timeout only really makes sense if we’re fetching a remote resource, so let’s group those two together in a new case class. case class RemoteConfig(uri: URI, timeout: Duration) val remoteOpts = (uriOpt, timeoutOpt).mapN(RemoteConfig.apply) // remoteOpts: Opts[RemoteConfig] = Opts(--uri &lt;uri&gt; [--timeout &lt;duration&gt;]) case class Config( remote: Option[RemoteConfig], file: Option[Path], output: Path, ) val configOpts = (remoteOpts.orNone, fileOpt.orNone, outputOpt) .mapN(Config.apply) .validate(\"must provide either uri or file\")(c =&gt; c.remote.isDefined ^ c.file.isDefined) // configOpts: Opts[Config] = Opts([--uri &lt;uri&gt; [--timeout &lt;duration&gt;]] [--input-file &lt;path&gt;] &lt;output-file&gt;) With this change, it’s no longer possible to define a Config that has a timeout but not a uri. This means we can get rid of one of our explicit validations; decline will do the equivalent input check automatically, and its autogenerated Usage: info in the help output will also reflect the new structure. Other benefits include: A single case class with twenty fields can get pretty unwieldy; it becomes easy to accidentally pass arguments in the wrong order, for example. Grouping arguments makes these errors less likely, both because small groups are easier to see at a glance and because the more explicit and specific your types are the more the compiler can help catch mistakes. It can make it easier to validate early – if we decided we wanted to ban custom timeouts when the URI was localhost, for example, we could add that validation to remoteOpts instead of configOpts. Mutual exclusion orElse is often used for subcommands, but it works just as well for ordinary options. Since our input file is always either local or remote, using orElse can let us express that more directly. // Either would for two mutually-exclusive possibilities, // but a sealed trait is a bit more general. sealed trait InputConfig case class RemoteConfig(uri: URI, timeout: Duration) extends InputConfig case class LocalConfig(file: Path) extends InputConfig val remoteOpts = (uriOpt, timeoutOpt).mapN(RemoteConfig.apply) // remoteOpts: Opts[RemoteConfig] = Opts(--uri &lt;uri&gt; [--timeout &lt;duration&gt;]) val localOpts = fileOpt.map(LocalConfig.apply) // localOpts: Opts[LocalConfig] = Opts(--input-file &lt;path&gt;) val inputOpts = remoteOpts orElse localOpts // inputOpts: Opts[Product with Serializable with InputConfig] = Opts(--uri &lt;uri&gt; [--timeout &lt;duration&gt;] | --input-file &lt;path&gt;) case class Config( input: InputConfig, queries: Path, ) val configOpts = (inputOpts, outputOpt).mapN(Config.apply) // configOpts: Opts[Config] = Opts(--uri &lt;uri&gt; [--timeout &lt;duration&gt;] &lt;output-file&gt; | --input-file &lt;path&gt; &lt;output-file&gt;) That gets rid of our final config validation; decline ensures that the user passes either --uri or --input-file, but never both. It’s often possible to replace ad-hoc validation with more precise data modelling like this, and it’s almost always a good idea when you can: it simplifies the code, improves the error messages and usage texts that decline generates, and helps “make illegal states unrepresentable” in your program. Config and effect style Through every step of the refactoring above, the shape of our command-line parser closely matches the Config datastructure; we build parsers for case classes using mapN and sealed traits using orElse, working our way up from simpler types to the full Config. (And once that’s done, we hand the whole thing off to some run function that does the actual work.) This “config” pattern is a very common way to structure an application using decline, and it’s easy to test: users often write unit tests that pass different arguments to the parser and assert that they parse successfully, or have the contents you’d expect. It’s also possible to avoid building up intermediate configs, instead just calling functions that take the appropriate action directly. // This example code uses cats-effect's IO, but the pattern works just as well for // imperative programs... just change the return type of the fetch functions // to `Future[String]`, `String`, or whatever else makes sense in your context. import cats.effect.IO def fetchRemote(uri: URI, timeout: Duration): IO[String] = ??? def fetchLocal(file: Path): IO[String] = ??? val remoteOpts = (uriOpt, timeoutOpt).mapN(fetchRemote) // remoteOpts: Opts[IO[String]] = Opts(--uri &lt;uri&gt; [--timeout &lt;duration&gt;]) val localOpts = fileOpt.map(fetchLocal) // localOpts: Opts[IO[String]] = Opts(--input-file &lt;path&gt;) val inputOpts = remoteOpts orElse localOpts // inputOpts: Opts[IO[String]] = Opts(--uri &lt;uri&gt; [--timeout &lt;duration&gt;] | --input-file &lt;path&gt;) def run(input: IO[String], output: Path): IO[Unit] = ??? val configOpts = (inputOpts, outputOpt).mapN(run) // configOpts: Opts[IO[Unit]] = Opts(--uri &lt;uri&gt; [--timeout &lt;duration&gt;] &lt;output-file&gt; | --input-file &lt;path&gt; &lt;output-file&gt;) This code is shorter and more direct than the config-based example, and avoids needing any intermediate data structures to represent the input. On the other hand, it’s somewhat more opaque and harder to test or validate. Either approach can work well, and some apps use both styles in different places. Follow your heart!"
    } ,    
    {
      "title": "Using Decline",
      "url": "/decline/usage.html",
      "content": "Using Decline Welcome to decline! Here, we’ll run through all of decline’s major features and look at how they fit together. decline is packaged under com.monovore.decline, so let’s pull that in: import com.monovore.decline._ Basic Options ‘Normal’ options take a single argument, with a specific type. (It’s important that you specify the type here; the compiler usually can’t infer it!) This lets you parse options like the -n50 in tail -n50. val lines = Opts.option[Int](\"lines\", short = \"n\", metavar = \"count\", help = \"Set a number of lines.\") // lines: Opts[Int] = Opts(--lines &lt;count&gt;) Flags are similar, but take no arguments. This is often used for ‘boolean’ flags, like the --quiet in tail --quiet. val quiet = Opts.flag(\"quiet\", help = \"Don't print any metadata to the console.\") // quiet: Opts[Unit] = Opts(--quiet) Positional arguments aren’t marked off by hyphens at all, but they do take a type parameter. This handles arguments like the file.txt in tail file.txt. import java.nio.file.Path val file = Opts.argument[Path](metavar = \"file\") // file: Opts[Path] = Opts(&lt;file&gt;) Each of these option types has a plural form, which are useful when you want users to pass the same kind of option multiple times. Instead of just returning a value A, repeated options and positional arguments will return a NonEmptyList[A], with all the values that were passed on the command line; repeated flags will return the number of times that flag was passed. val settings = Opts.options[String](\"setting\", help = \"...\") // settings: Opts[cats.data.NonEmptyList[String]] = Opts(--setting &lt;string&gt; [--setting &lt;string&gt;]...) val verbose = Opts.flags(\"verbose\", help = \"Print extra metadata to the console.\") // verbose: Opts[Int] = Opts(--verbose [--verbose]...) val files = Opts.arguments[String](\"file\") // files: Opts[cats.data.NonEmptyList[String]] = Opts(&lt;file&gt;...) You can also read a value directly from an environment variable. val port = Opts.env[Int](\"PORT\", help = \"The port to run on.\") // port: Opts[Int] = Opts() Default Values All of the above options are required: if they’re missing, the parser will complain. We can allow missing values with the withDefault method: val linesOrDefault = lines.withDefault(10) // linesOrDefault: Opts[Int] = Opts([--lines &lt;count&gt;]) That returns a new Opts[Int] instance… but this one can always return a value, whether or not --lines is passed on the command line. There’s a few more handy combinators for some particularly common cases: val optionalFile = file.orNone // optionalFile: Opts[Option[Path]] = Opts([&lt;file&gt;]) val fileList = files.orEmpty // fileList: Opts[List[String]] = Opts([&lt;file&gt;...]) val quietOrNot = quiet.orFalse // quietOrNot: Opts[Boolean] = Opts([--quiet]) Transforming and Validating Like many other Scala types, Opts can be mapped over. lines.map { _.toString } // res0: Opts[String] = Opts(--lines &lt;count&gt;) validate is much like filter – the parser will fail if the parsed value doesn’t match the given function – but it comes with a spot for a better error message. mapValidated lets you validate and transform at once, since that’s sometimes useful. import cats.data.Validated val validated = lines.validate(\"Must be positive!\") { _ &gt; 0 } // validated: Opts[Int] = Opts(--lines &lt;count&gt;) val both = lines.mapValidated { n =&gt; if (n &gt; 0) Validated.valid(n.toString) else Validated.invalidNel(\"Must be positive!\") } // both: Opts[String] = Opts(--lines &lt;count&gt;) Combining Options You can combine multiple Opts instances using cats’ applicative syntax: import cats.implicits._ val tailOptions = (linesOrDefault, fileList).mapN { (n, files) =&gt; println(s\"LOG: Printing the last $n lines from each file in $files!\") } // tailOptions: Opts[Unit] = Opts([--lines &lt;count&gt;] [&lt;file&gt;...]) tupled is a useful operation when you want to compose into a larger Opts that yields a tuple: import cats.implicits._ val tailOptionsTuple = (linesOrDefault, fileList).tupled // tailOptionsTuple: Opts[(Int, List[String])] = Opts([--lines &lt;count&gt;] [&lt;file&gt;...]) Other options are mutually exclusive: you might want to pass --verbose to make a command noisier, or --quiet to make it quieter, but it doesn’t make sense to do both at once! val verbosity = verbose orElse quiet.map { _ =&gt; -1 } withDefault 0 // verbosity: Opts[Int] = Opts([--verbose [--verbose]... | --quiet]) When you combine Opts instances with orElse like this, the parser will choose the first alternative that matches the given command-line arguments. Commands and Subcommands A Command bundles up an Opts instance with some extra metadata, like a command name and description. val tailCommand = Command( name = \"tail\", header = \"Print the last few lines of one or more files.\" ) { tailOptions } // tailCommand: Command[Unit] = com.monovore.decline.Command@5f97a6cf To embed the command as part of a larger application, you can wrap it up as a subcommand. val tailSubcommand = Opts.subcommand(tailCommand) // tailSubcommand: Opts[Unit] = Opts(tail) … or, equivalently and more concisely… val tailSubcommand2 = Opts.subcommand(\"tail\", help = \"Print the few lines of one or more files.\") { tailOptions } // tailSubcommand2: Opts[Unit] = Opts(tail) A subcommand is an instance of Opts… and can be transformed, nested, and combined just like any other option type. (If you’re supporting multiple subcommands, the orElse method is particularly useful: tailSubcommand orElse otherSubcommand orElse ....) Parsing Arguments Commands aren’t just useful for defining subcommands – they’re also used to parse an array of command-line arguments directly. Calling parse returns either the parsed value, if the arguments were good, or a help text if something went wrong. tailCommand.parse(Seq(\"-n50\", \"foo.txt\", \"bar.txt\")) // LOG: Printing the last 50 lines from each file in List(foo.txt, bar.txt)! // res1: Either[Help, Unit] = Right(()) tailCommand.parse(Seq(\"--mystery-option\")) // res2: Either[Help, Unit] = Left(Unexpected option: --mystery-option // // Usage: tail [--lines &lt;count&gt;] [&lt;file&gt;...] // // Print the last few lines of one or more files. // // Options and flags: // --help // Display this help text. // --lines &lt;count&gt;, -n &lt;count&gt; // Set a number of lines.) If your parser reads environment variables, you’ll want to pass in the environment as well. tailCommand.parse(Seq(\"foo.txt\"), sys.env) // LOG: Printing the last 10 lines from each file in List(foo.txt)! // res3: Either[Help, Unit] = Right(()) A main method that uses decline for argument parsing would look something like: def main(args: Array[String]) = tailCommand.parse(args, sys.env) match { case Left(help) if help.errors.isEmpty =&gt; // help was requested by the user, i.e.: `--help` println(help) sys.exit(0) case Left(help) =&gt; // user needs help due to bad/missing arguments System.err.println(help) sys.exit(1) case Right(parsedValue) =&gt; // Your program goes here! } This handles arguments and environment variables correctly, and reports any bad arguments clearly to the user. Using CommandApp If you have a Command[Unit], extending CommandApp will wire up that main method for you. object Tail extends CommandApp(tailCommand) The resulting application can be called like any other Java app. Instead of defining a separate command, it’s often easier to just define everything inline: object TailApp extends CommandApp( name = \"tail\", header = \"Print the last few lines of one or more files.\", main = (linesOrDefault, fileList).mapN { (n, files) =&gt; println(s\"LOG: Printing the last $n lines from each file in $files!\") } ) That’s it! If you made it this far, you might be interested in more supported argument types, cats-effect integration or Scala.js support."
    }    
  ];

  idx = lunr(function () {
    this.ref("title");
    this.field("content");

    docs.forEach(function (doc) {
      this.add(doc);
    }, this);
  });

  docs.forEach(function (doc) {
    docMap.set(doc.title, doc.url);
  });
}

// The onkeypress handler for search functionality
function searchOnKeyDown(e) {
  const keyCode = e.keyCode;
  const parent = e.target.parentElement;
  const isSearchBar = e.target.id === "search-bar";
  const isSearchResult = parent ? parent.id.startsWith("result-") : false;
  const isSearchBarOrResult = isSearchBar || isSearchResult;

  if (keyCode === 40 && isSearchBarOrResult) {
    // On 'down', try to navigate down the search results
    e.preventDefault();
    e.stopPropagation();
    selectDown(e);
  } else if (keyCode === 38 && isSearchBarOrResult) {
    // On 'up', try to navigate up the search results
    e.preventDefault();
    e.stopPropagation();
    selectUp(e);
  } else if (keyCode === 27 && isSearchBarOrResult) {
    // On 'ESC', close the search dropdown
    e.preventDefault();
    e.stopPropagation();
    closeDropdownSearch(e);
  }
}

// Search is only done on key-up so that the search terms are properly propagated
function searchOnKeyUp(e) {
  // Filter out up, down, esc keys
  const keyCode = e.keyCode;
  const cannotBe = [40, 38, 27];
  const isSearchBar = e.target.id === "search-bar";
  const keyIsNotWrong = !cannotBe.includes(keyCode);
  if (isSearchBar && keyIsNotWrong) {
    // Try to run a search
    runSearch(e);
  }
}

// Move the cursor up the search list
function selectUp(e) {
  if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index) && (index > 0)) {
      const nextIndexStr = "result-" + (index - 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Move the cursor down the search list
function selectDown(e) {
  if (e.target.id === "search-bar") {
    const firstResult = document.querySelector("li[id$='result-0']");
    if (firstResult) {
      firstResult.firstChild.focus();
    }
  } else if (e.target.parentElement.id.startsWith("result-")) {
    const index = parseInt(e.target.parentElement.id.substring(7));
    if (!isNaN(index)) {
      const nextIndexStr = "result-" + (index + 1);
      const querySel = "li[id$='" + nextIndexStr + "'";
      const nextResult = document.querySelector(querySel);
      if (nextResult) {
        nextResult.firstChild.focus();
      }
    }
  }
}

// Search for whatever the user has typed so far
function runSearch(e) {
  if (e.target.value === "") {
    // On empty string, remove all search results
    // Otherwise this may show all results as everything is a "match"
    applySearchResults([]);
  } else {
    const tokens = e.target.value.split(" ");
    const moddedTokens = tokens.map(function (token) {
      // "*" + token + "*"
      return token;
    })
    const searchTerm = moddedTokens.join(" ");
    const searchResults = idx.search(searchTerm);
    const mapResults = searchResults.map(function (result) {
      const resultUrl = docMap.get(result.ref);
      return { name: result.ref, url: resultUrl };
    })

    applySearchResults(mapResults);
  }

}

// After a search, modify the search dropdown to contain the search results
function applySearchResults(results) {
  const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
  if (dropdown) {
    //Remove each child
    while (dropdown.firstChild) {
      dropdown.removeChild(dropdown.firstChild);
    }

    //Add each result as an element in the list
    results.forEach(function (result, i) {
      const elem = document.createElement("li");
      elem.setAttribute("class", "dropdown-item");
      elem.setAttribute("id", "result-" + i);

      const elemLink = document.createElement("a");
      elemLink.setAttribute("title", result.name);
      elemLink.setAttribute("href", result.url);
      elemLink.setAttribute("class", "dropdown-item-link");

      const elemLinkText = document.createElement("span");
      elemLinkText.setAttribute("class", "dropdown-item-link-text");
      elemLinkText.innerHTML = result.name;

      elemLink.appendChild(elemLinkText);
      elem.appendChild(elemLink);
      dropdown.appendChild(elem);
    });
  }
}

// Close the dropdown if the user clicks (only) outside of it
function closeDropdownSearch(e) {
  // Check if where we're clicking is the search dropdown
  if (e.target.id !== "search-bar") {
    const dropdown = document.querySelector("div[id$='search-dropdown'] > .dropdown-content.show");
    if (dropdown) {
      dropdown.classList.remove("show");
      document.documentElement.removeEventListener("click", closeDropdownSearch);
    }
  }
}
