package com.monovore.decline

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class EnvVarSpec extends AnyWordSpec with Matchers {

  private def command[A](opts: Opts[A]): Command[A] =
    Command("test", "Test command", helpFlag = false)(opts)

  "Help.envVars" should {

    "return Required for a bare env var" in {
      val opts = Opts.env[String]("DATABASE_URL", help = "Connection string", metavar = "uri")
      Help.envVars(command(opts)) shouldBe List(
        EnvVar("DATABASE_URL", "uri", "Connection string", EnvVarDefault.Required)
      )
    }

    "return a Value default for withDefault" in {
      val opts = Opts.env[String]("LOG_LEVEL", help = "Log level").withDefault("INFO")
      Help.envVars(command(opts)) shouldBe List(
        EnvVar("LOG_LEVEL", "string", "Log level", EnvVarDefault.Value("INFO"))
      )
    }

    "return Optional for orNone" in {
      val opts = Opts.env[String]("CACHE_TTL", help = "Cache TTL in seconds").orNone
      Help.envVars(command(opts)) shouldBe List(
        EnvVar("CACHE_TTL", "string", "Cache TTL in seconds", EnvVarDefault.Optional)
      )
    }

    "return the correct Value default for orFalse (concrete false default)" in {
      // orFalse is withDefault(false), so the default is the string "false"
      val opts = Opts.env[String]("FEATURE_FLAG", help = "Feature toggle").map(_ => ()).orFalse
      Help.envVars(command(opts)) shouldBe List(
        EnvVar("FEATURE_FLAG", "string", "Feature toggle", EnvVarDefault.Value("false"))
      )
    }

    "propagate withDefault through mapN (App) as the combined-result string" in {
      // withDefault on a combined parser tags all constituent env vars with the
      // combined default's toString — the best approximation without re-parsing
      val opts = (
        Opts.env[String]("DB_HOST", help = "Database host"),
        Opts.env[String]("DB_PORT", help = "Database port")
      ).tupled.withDefault(("localhost", "5432"))
      val vars = Help.envVars(command(opts))
      vars.map(_.name) should contain allOf ("DB_HOST", "DB_PORT")
      vars.foreach(_.default shouldBe EnvVarDefault.Value("(localhost,5432)"))
    }

    "treat an env var on the right of orElse as Required when the right side is not a constant" in {
      // option orElse env: env is a non-constant alternative, not a default
      val opts = Opts.option[String]("host", help = "Host flag") orElse
        Opts.env[String]("HOST", help = "Host env var")
      Help.envVars(command(opts)) shouldBe List(
        EnvVar("HOST", "string", "Host env var", EnvVarDefault.Required)
      )
    }

    "deduplicate by name, keeping the first occurrence" in {
      val env = Opts.env[String]("PORT", help = "Port", metavar = "int")
      val opts = (env, env).tupled
      Help.envVars(command(opts)) shouldBe List(
        EnvVar("PORT", "int", "Port", EnvVarDefault.Required)
      )
    }

    "prefer the first occurrence when the same name appears with different defaults" in {
      val withDefault = Opts.env[String]("HOST", help = "Host").withDefault("localhost")
      val required    = Opts.env[String]("HOST", help = "Host")
      val opts = (withDefault, required).tupled
      Help.envVars(command(opts)) shouldBe List(
        EnvVar("HOST", "string", "Host", EnvVarDefault.Value("localhost"))
      )
    }

    "return Nil for opts with no env vars" in {
      val opts = Opts.option[String]("name", help = "A flag-only parser")
      Help.envVars(command(opts)) shouldBe Nil
    }

    "collect independent env vars from mapN" in {
      val opts = (
        Opts.env[String]("HOST", help = "Host").withDefault("localhost"),
        Opts.env[Int]("PORT", help = "Port").withDefault(5432)
      ).tupled
      val vars = Help.envVars(command(opts))
      vars.map(_.name) shouldBe List("HOST", "PORT")
      vars.find(_.name == "HOST").map(_.default) shouldBe Some(EnvVarDefault.Value("localhost"))
      vars.find(_.name == "PORT").map(_.default) shouldBe Some(EnvVarDefault.Value("5432"))
    }
  }

  "Help rendering with env var defaults" should {

    "show the default value in --help output" in {
      val opts = Opts.env[String]("LOG_LEVEL", help = "Log level").withDefault("INFO")
      val help = Help.fromCommand(command(opts)).toString
      help should include("LOG_LEVEL=<string>")
      help should include("Log level (default: INFO)")
    }

    "show (optional) in --help output for orNone vars" in {
      val opts = Opts.env[String]("CACHE_TTL", help = "Cache TTL").orNone
      val help = Help.fromCommand(command(opts)).toString
      help should include("Cache TTL (optional)")
    }

    "show no suffix for required vars" in {
      val opts = Opts.env[String]("DATABASE_URL", help = "Connection string")
      val help = Help.fromCommand(command(opts)).toString
      help should include("Connection string")
      help should not include "(default:"
      help should not include "(optional)"
    }

    "show defaults via environmentVarHelpLines" in {
      val opts = (
        Opts.env[String]("HOST", help = "Hostname").withDefault("localhost"),
        Opts.env[String]("SECRET", help = "API secret")
      ).tupled
      val lines = Help.environmentVarHelpLines(opts)
      lines should contain("HOST=<string>")
      lines.find(_ contains "Hostname") shouldBe Some("    Hostname (default: localhost)")
      lines should contain("SECRET=<string>")
      lines.find(_ contains "API secret") shouldBe Some("    API secret")
    }

    "de-duplicate env vars in rendered output" in {
      val env  = Opts.env[String]("PORT", help = "Port")
      val opts = (env, env).tupled
      val help = Help.fromCommand(command(opts)).toString
      help.split("PORT=").length shouldBe 2 // appears exactly once
    }
  }
}
