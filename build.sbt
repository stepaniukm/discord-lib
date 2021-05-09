import Dependencies._

ThisBuild / scalaVersion     := "2.13.5"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "cats-test",
    libraryDependencies += scalaTest % Test
  )

val AkkaVersion = "2.6.14"
val AkkaHttpVersion = "10.2.4"
libraryDependencies ++= Seq(
  "com.typesafe" % "config" % "1.4.1",
  "com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion,
  "com.typesafe.akka" %% "akka-stream" % AkkaVersion,
  "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
  "org.typelevel" %% "cats-core" % "2.3.0",
  "org.typelevel" %% "cats-effect" % "3.1.0",
  "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
  "com.github.julien-truffaut" %% "monocle-core"  % "3.0.0-M5",
  "com.github.julien-truffaut" %% "monocle-macro" % "3.0.0-M5",
)

scalacOptions in Global += "-Ymacro-annotations"

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
