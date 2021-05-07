import Dependencies._

ThisBuild / scalaVersion     := "3.0.0-RC2"
ThisBuild / version          := "0.1.0-SNAPSHOT"
ThisBuild / organization     := "com.example"
ThisBuild / organizationName := "example"

lazy val root = (project in file("."))
  .settings(
    name := "cats-test",
    libraryDependencies += (scalaTest % Test).cross(CrossVersion.for3Use2_13)
  )

crossScalaVersions ++= Seq("2.13.5", "3.0.0-RC2")

val AkkaVersion = "2.6.8"
val AkkaHttpVersion = "10.2.4"
libraryDependencies ++= Seq(
  ("com.typesafe.akka" %% "akka-actor-typed" % AkkaVersion).cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-stream" % AkkaVersion).cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-http" % AkkaHttpVersion).cross(CrossVersion.for3Use2_13),
  ("org.typelevel" %% "cats-core" % "2.3.0").cross(CrossVersion.for3Use2_13),
  ("org.typelevel" %% "cats-effect" % "3.1.0").cross(CrossVersion.for3Use2_13),
  ("com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion).cross(CrossVersion.for3Use2_13)
)

// See https://www.scala-sbt.org/1.x/docs/Using-Sonatype.html for instructions on how to publish to Sonatype.
