name := "decline"

description := "Composable command-line parsing for Scala"

organization := "com.monovore"

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.2")

libraryDependencies += "org.typelevel" %% "cats" % "0.7.2"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0" % "test"

scalaVersion := "2.11.7"

scalacOptions ++= Seq("-Xfatal-warnings", "-deprecation", "-feature", "-language:higherKinds")

enablePlugins(MicrositesPlugin)

micrositeConfigYaml := microsites.ConfigYml(
  yamlInline = """kramdown: { input: GFM, hard_wrap: false }"""
)

micrositeGithubOwner := "bkirwi"

micrositeGithubRepo := "decline"

micrositeHighlightTheme := "solarized-light"

micrositeDocumentationUrl := "usage.html"