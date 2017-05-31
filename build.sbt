name := "decline"

description := "Composable command-line parsing for Scala"

organization := "com.monovore"

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3" cross CrossVersion.binary)

libraryDependencies += "org.typelevel" %% "cats" % "0.9.0"

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.0.0" % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.3" % "test"
)

scalaVersion := "2.11.7"

crossScalaVersions := List("2.11.7", "2.12.1")

scalacOptions ++= Seq("-Xfatal-warnings", "-deprecation", "-feature", "-language:higherKinds")

licenses += ("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html"))

enablePlugins(MicrositesPlugin)

micrositeConfigYaml := microsites.ConfigYml(
  yamlInline = """kramdown: { input: GFM, hard_wrap: false }"""
)

micrositeBaseUrl := "/decline"

micrositeGithubOwner := "bkirwi"

micrositeGithubRepo := "decline"

micrositeHighlightTheme := "solarized-light"

micrositeDocumentationUrl := "usage.html"

releaseCrossBuild := true
