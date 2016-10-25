name := "decline"

organization := "com.monovore"

resolvers += Resolver.sonatypeRepo("releases")

addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.2")

libraryDependencies += "org.typelevel" %% "cats" % "0.7.2"

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.0" % "test"

scalaVersion := "2.11.7"

scalacOptions ++= Seq("-Xfatal-warnings", "-deprecation", "-feature", "-language:higherKinds")
