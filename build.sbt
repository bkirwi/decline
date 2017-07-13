
enablePlugins(ScalaJSPlugin)

scalaVersion in ThisBuild := "2.11.11"

crossScalaVersions in ThisBuild := List("2.11.7", "2.12.1")

resolvers in ThisBuild += Resolver.sonatypeRepo("releases")

homepage in ThisBuild := Some(url("http://monovore.com/decline"))

organization in ThisBuild := "com.monovore"

scalacOptions in ThisBuild ++= Seq("-Xfatal-warnings", "-deprecation", "-feature", "-language:higherKinds")

licenses in ThisBuild += ("Apache-2.0" -> url("https://www.apache.org/licenses/LICENSE-2.0.html"))

releaseCrossBuild in ThisBuild := true

releasePublishArtifactsAction in ThisBuild := PgpKeys.publishSigned.value

useGpg in ThisBuild := true

lazy val noPublishSettings = Seq(
  publish := {},
  publishLocal := {}
)

lazy val root =
  project.in(file("."))
    .aggregate(declineJS, declineJVM, doc)
    .settings(noPublishSettings)

lazy val decline =
  crossProject.in(file("core"))
    .settings(addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.3" cross CrossVersion.binary): _*)
    .settings(
      name := "decline",
      description := "Composable command-line parsing for Scala",
      libraryDependencies += "org.typelevel" %%% "cats" % "0.9.0",
      libraryDependencies ++= Seq(
        "org.scalatest" %%% "scalatest" % "3.0.0" % "test",
        "org.scalacheck" %%% "scalacheck" % "1.13.3" % "test"
      )
    )


lazy val declineJVM = decline.jvm
lazy val declineJS = decline.js

lazy val doc =
  project.in(file("doc"))
    .dependsOn(declineJVM)
    .enablePlugins(MicrositesPlugin)
    .settings(noPublishSettings)
    .settings(
      micrositeConfigYaml := microsites.ConfigYml(
        yamlInline = """kramdown: { input: GFM, hard_wrap: false }"""
      ),
      micrositeBaseUrl := "/decline",
      micrositeGithubOwner := "bkirwi",
      micrositeGithubRepo := "decline",
      micrositeHighlightTheme := "solarized-light",
      micrositeDocumentationUrl := "usage.html"
    )

