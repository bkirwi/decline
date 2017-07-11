


enablePlugins(MicrositesPlugin)
enablePlugins(ScalaJSPlugin)

lazy val root =
  project.in(file("."))
    .aggregate(declineJS, declineJVM)
    .settings(
      publish := {},
      publishLocal := {}
    )

lazy val decline =
  crossProject.in(file("core"))
    .settings(addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3" cross CrossVersion.binary): _*)
    .settings(
      name := "decline",
      description := "Composable command-line parsing for Scala",
      organization := "com.monovore",
      homepage := Some(url("http://monovore.com/decline")),
      resolvers += Resolver.sonatypeRepo("releases"),
      libraryDependencies += "org.typelevel" %% "cats" % "0.9.0",
      libraryDependencies ++= Seq(
        "org.scalatest" %% "scalatest" % "3.0.0" % "test",
        "org.scalacheck" %% "scalacheck" % "1.13.3" % "test"
      ),
      scalaVersion := "2.11.11",
      crossScalaVersions := List("2.11.7", "2.12.1"),
      scalacOptions ++= Seq("-Xfatal-warnings", "-deprecation", "-feature", "-language:higherKinds"),
      licenses += ("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")),
      micrositeConfigYaml := microsites.ConfigYml(
        yamlInline = """kramdown: { input: GFM, hard_wrap: false }"""
      ),
      micrositeBaseUrl := "/decline",
      micrositeGithubOwner := "bkirwi",
      micrositeGithubRepo := "decline",
      micrositeHighlightTheme := "solarized-light",
      micrositeDocumentationUrl := "usage.html",
      releaseCrossBuild := true,
      releasePublishArtifactsAction := PgpKeys.publishSigned.value,
      useGpg := true
    )
    .jsSettings(
      scalaJSUseMainModuleInitializer := true
    )

lazy val declineJVM = decline.jvm
lazy val declineJS = decline.js

