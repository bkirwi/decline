
enablePlugins(ScalaJSPlugin)

val defaultSettings = Seq(
  scalaVersion := "2.11.11",
  crossScalaVersions := List("2.11.11", "2.12.3"),
  resolvers += Resolver.sonatypeRepo("releases"),
  homepage := Some(url("http://monovore.com/decline")),
  organization := "com.monovore",
  scalacOptions ++= Seq("-Xfatal-warnings", "-deprecation", "-feature", "-language:higherKinds"),
  licenses += ("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  pomExtra := (
    <scm>
      <url>git@github.com:bkirwi/decline.git</url>
      <connection>scm:git:git@github.com:bkirwi/decline.git</connection>
    </scm>
    <developers>
      <developer>
        <id>bkirwi</id>
        <name>Ben Kirwin</name>
        <url>http://ben.kirw.in/</url>
      </developer>
    </developers>
  ),
  publishMavenStyle := true,
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false }
)

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

lazy val root =
  project.in(file("."))
    .aggregate(declineJS, declineJVM, refinedJS, refinedJVM, doc)
    .settings(defaultSettings)
    .settings(noPublishSettings)

lazy val decline =
  crossProject.in(file("core"))
    .settings(defaultSettings)
    .settings(addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3" cross CrossVersion.binary))
    .settings(
      name := "decline",
      description := "Composable command-line parsing for Scala",
      libraryDependencies ++= {
        val catsVersion = "1.0.0-RC2"

        Seq(
          "org.typelevel"  %%% "cats-core"  % catsVersion,
          "org.typelevel"  %%% "cats-laws"  % catsVersion % "test",
          "org.scalatest"  %%% "scalatest"  % "3.0.0" % "test",
          "org.scalacheck" %%% "scalacheck" % "1.13.5" % "test"
        )
      }
    )

lazy val declineJVM = decline.jvm
lazy val declineJS = decline.js

lazy val refined =
  crossProject.crossType(CrossType.Pure).in(file("refined"))
    .settings(defaultSettings)
    .settings(
      name := "refined",
      moduleName := "decline-refined",
      libraryDependencies += "eu.timepit" %%% "refined" % "0.8.4"
    )
    .dependsOn(decline % "compile->compile;test->test")

lazy val refinedJVM = refined.jvm
lazy val refinedJS = refined.js

lazy val doc =
  project.in(file("doc"))
    .dependsOn(declineJVM, refinedJVM)
    .enablePlugins(MicrositesPlugin)
    .settings(defaultSettings)
    .settings(noPublishSettings)
    .settings(
      micrositeName := "decline",
      micrositeDescription := "Composable command-line parsing for Scala",
      micrositeConfigYaml := microsites.ConfigYml(
        yamlInline = """kramdown: { input: GFM, hard_wrap: false }"""
      ),
      micrositeBaseUrl := "/decline",
      micrositeGithubOwner := "bkirwi",
      micrositeGithubRepo := "decline",
      micrositeHighlightTheme := "solarized-light",
      micrositeDocumentationUrl := "usage.html"
    )
