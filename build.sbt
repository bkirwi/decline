import ReleaseTransformations._
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import sbtcrossproject.CrossType
import microsites._

ThisBuild / mimaFailOnNoPrevious := false
val mimaPreviousVersion = "1.0.0"

lazy val Scala212 = "2.12.12"
lazy val Scala213 = "2.13.5"
lazy val Scala3 = "3.0.1"

ThisBuild / scalaVersion := Scala212
ThisBuild / crossScalaVersions := List(Scala212, Scala213, Scala3)
ThisBuild / githubWorkflowArtifactUpload := false
ThisBuild / githubWorkflowPublishTargetBranches := Seq()
ThisBuild / githubWorkflowUseSbtThinClient := false

val defaultSettings = Seq(
  resolvers += Resolver.sonatypeRepo("releases"),
  homepage := Some(url("http://monovore.com/decline")),
  organization := "com.monovore",
  scalacOptions ++= Seq("-deprecation", "-feature", "-language:higherKinds"),
  scalacOptions ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 12)) =>
        Seq("-Xfatal-warnings")
      case Some((3, _)) =>
        Seq("-Ykind-projector", "-Ytasty-reader")
      case _ =>
        Nil
    }
  },
  licenses += ("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html")),
  releaseCrossBuild := true,
  releasePublishArtifactsAction := PgpKeys.publishSigned.value,
  Global / PgpKeys.gpgCommand := "/usr/bin/gpg2",
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/bkirwi/decline"),
      "git@github.com:bkirwi/decline.git"
    )
  ),
  pomExtra := (
    <developers>
      <developer>
        <id>bkirwi</id>
        <name>Ben Kirwin</name>
        <url>http://ben.kirw.in/</url>
      </developer>
    </developers>
  ),
  publishMavenStyle := true,
  Test / publishArtifact := false,
  pomIncludeRepository := { _ => false },
  publishTo := Some(
    if (isSnapshot.value) Opts.resolver.sonatypeSnapshots
    else Opts.resolver.sonatypeStaging
  ),
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runClean,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    publishArtifacts,
    releaseStepCommand("sonatypeReleaseAll"),
    setNextVersion,
    commitNextVersion,
    pushChanges
  )
)

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

val catsVersion = "2.6.1"

val catsEffectVersion = "3.2.1"

lazy val root =
  project
    .in(file("."))
    .aggregate(declineJS, declineJVM, refinedJS, refinedJVM, effectJS, effectJVM, doc)
    .settings(defaultSettings)
    .settings(noPublishSettings)

lazy val decline =
  crossProject(JSPlatform, JVMPlatform)
    .in(file("core"))
    .settings(defaultSettings)
    .settings(
      libraryDependencies ++= {
        if (scalaVersion.value.startsWith("2."))
          Seq(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.0" cross CrossVersion.full))
        else
          Seq.empty
      }
    )
    .settings(
      name := "decline",
      description := "Composable command-line parsing for Scala",
      libraryDependencies ++= Seq(
        "org.typelevel" %%% "cats-core" % catsVersion,
        "org.typelevel" %%% "cats-laws" % catsVersion % Test,
        "org.typelevel" %%% "discipline-scalatest" % "2.1.5" % Test
      )
    )
    .jvmSettings(
      mimaPreviousArtifacts := Set(organization.value %% moduleName.value % mimaPreviousVersion)
    )
    .jsSettings(
      libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.3.0",
      coverageEnabled := false
    )

lazy val declineJVM = decline.jvm
lazy val declineJS = decline.js
  .enablePlugins(ScalaJSPlugin)

lazy val bench =
  project
    .in(file("bench"))
    .enablePlugins(JmhPlugin)
    .dependsOn(declineJVM, refinedJVM)
    .settings(defaultSettings)
    .settings(noPublishSettings)
    .settings(fork := true)

lazy val refined =
  crossProject(JSPlatform, JVMPlatform)
    .crossType(CrossType.Pure)
    .in(file("refined"))
    .settings(defaultSettings)
    .settings(
      name := "refined",
      moduleName := "decline-refined",
      libraryDependencies ++= {
        val refinedVersion = "0.9.26"

        Seq(
          "eu.timepit" %%% "refined" % refinedVersion,
          "eu.timepit" %%% "refined-scalacheck" % refinedVersion % "test"
        )
      }
    )
    .dependsOn(decline % "compile->compile;test->test")
    .jvmSettings(
      mimaPreviousArtifacts := Set(organization.value %% moduleName.value % mimaPreviousVersion)
    )
    .jsSettings(coverageEnabled := false)

lazy val refinedJVM = refined.jvm
lazy val refinedJS = refined.js

lazy val effect =
  crossProject(JSPlatform, JVMPlatform)
    .in(file("effect"))
    .settings(defaultSettings)
    .settings(
      name := "effect",
      moduleName := "decline-effect",
      libraryDependencies ++= Seq(
        "org.typelevel" %%% "cats-effect" % catsEffectVersion
      )
    )
    .dependsOn(decline % "compile->compile;test->test")
    .jvmSettings(
      mimaPreviousArtifacts := Set(organization.value %% moduleName.value % mimaPreviousVersion)
    )
    .jsSettings(coverageEnabled := false)

lazy val effectJVM = effect.jvm
lazy val effectJS = effect.js

lazy val doc =
  project
    .in(file("doc"))
    .dependsOn(declineJVM, refinedJVM, effectJVM)
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
      micrositeGitterChannel := false,
      micrositeShareOnSocial := false,
      micrositeHighlightTheme := "solarized-light",
      micrositeDocumentationUrl := "usage.html",
      micrositeTheme := "pattern",
      micrositePalette := Map(
        "brand-primary" -> "#B58900",
        "brand-secondary" -> "#073642",
        "brand-tertiary" -> "#002b36",
        "gray-dark" -> "#453E46",
        "gray" -> "#837F84",
        "gray-light" -> "#E3E2E3",
        "gray-lighter" -> "#F4F3F4",
        "white-color" -> "#fdf6e3"
      ),
      mdocVariables := Map(
        "DECLINE_VERSION" -> version.value
      ),
      mdocIn := file("doc/src/main/tut")
    )
