import ReleaseTransformations.*
import com.typesafe.tools.mima.core.{Problem, ProblemFilters}
import sbtcrossproject.CrossPlugin.autoImport.crossProject
import sbtcrossproject.CrossType
import microsites.*

ThisBuild / mimaFailOnNoPrevious := false
val mimaPreviousVersion = "2.2.0"

lazy val Scala212 = "2.12.20"
lazy val Scala213 = "2.13.15"
lazy val Scala3 = "3.3.6"

ThisBuild / scalaVersion := Scala212
ThisBuild / crossScalaVersions := List(Scala212, Scala213, Scala3)
ThisBuild / githubWorkflowSbtCommand := "sbt -mem 4000"
ThisBuild / githubWorkflowArtifactUpload := false
ThisBuild / githubWorkflowPublishTargetBranches := Seq()
ThisBuild / githubWorkflowUseSbtThinClient := false
ThisBuild / githubWorkflowBuild += WorkflowStep.Sbt(
  List("declineNative/test"),
  name = Some("Test Scala-Native")
)
ThisBuild / githubWorkflowBuild += WorkflowStep.Sbt(
  List("mimaReportBinaryIssues"),
  name = Some("Report MiMa binary issues")
)

val defaultSettings = Seq(
  resolvers ++= Resolver.sonatypeOssRepos("releases"),
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
  ),
  mimaBinaryIssueFilters ++= Seq(
    ProblemFilters.exclude[Problem]("com.monovore.decline.Result*")
  )
)

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {},
  publishArtifact := false
)

val catsVersion = "2.12.0"

val catsEffectVersion = "3.4.0"

lazy val root =
  project
    .in(file("."))
    .aggregate(
      declineJS,
      declineJVM,
      declineNative,
      refinedJS,
      refinedJVM,
      refinedNative,
      effectJS,
      effectJVM,
      doc
    )
    .settings(defaultSettings)
    .settings(noPublishSettings)

lazy val decline =
  crossProject(JSPlatform, JVMPlatform, NativePlatform)
    .in(file("core"))
    .settings(defaultSettings)
    .settings(
      libraryDependencies ++= {
        if (scalaVersion.value.startsWith("2."))
          Seq(compilerPlugin("org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full))
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
        "org.typelevel" %%% "discipline-scalatest" % "2.3.0" % Test
      )
    )
    .jvmSettings(
      mimaPreviousArtifacts := Set(organization.value %% moduleName.value % mimaPreviousVersion)
    )
    .platformsSettings(JSPlatform, NativePlatform)(
      libraryDependencies += "io.github.cquiroz" %%% "scala-java-time" % "2.6.0",
      coverageEnabled := false
    )

lazy val declineJVM = decline.jvm
lazy val declineJS = decline.js
  .enablePlugins(ScalaJSPlugin)
lazy val declineNative: Project = decline.native
  .enablePlugins(ScalaNativePlugin)

lazy val bench =
  project
    .in(file("bench"))
    .enablePlugins(JmhPlugin)
    .dependsOn(declineJVM, refinedJVM)
    .settings(defaultSettings)
    .settings(noPublishSettings)
    .settings(fork := true)

lazy val refined =
  crossProject(JSPlatform, JVMPlatform, NativePlatform)
    .crossType(CrossType.Pure)
    .in(file("refined"))
    .settings(defaultSettings)
    .settings(
      name := "refined",
      moduleName := "decline-refined",
      libraryDependencies ++= {
        val refinedVersion = "0.11.3"

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
lazy val refinedNative = refined.native

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
    .platformsSettings(JSPlatform)(coverageEnabled := false)

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
