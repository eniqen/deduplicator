import com.typesafe.sbt.packager.docker._

name := "deduplicator"

version := "0.1"

scalaVersion := "2.12.10"

val circeV = "0.13.0"
val enumeratumV = "1.6.1"
val http4sV = "0.21.6"
val kindProjectorV= "0.11.0"

val circeDeps = Seq(
  "io.circe" %% "circe-core",
  "io.circe" %% "circe-generic",
  "io.circe" %% "circe-parser",
  "io.circe" %% "circe-optics"
).map(_ % circeV)

val enumeratumDeps = Seq(
  "com.beachape" %% "enumeratum",
  "com.beachape" %% "enumeratum-circe"
).map(_ % enumeratumV)

val http4sDeps = Seq(
  "org.http4s" %% "http4s-blaze-server" % http4sV,
  "org.http4s" %% "http4s-blaze-client" % http4sV,
  "org.http4s" %% "http4s-circe" % http4sV,
  "org.http4s" %% "http4s-dsl" % http4sV,
  "org.http4s" %% "http4s-client" % http4sV
)

val mainDeps = Seq(
  "com.github.alexandrnikitin" %% "bloom-filter" % "latest.release",
  "ch.qos.logback" % "logback-classic" % "1.2.3"
)

val testDeps = Seq(
  "org.scalatest" %% "scalatest" % "3.1.1"
).map(_ % "test")

libraryDependencies ++=
    circeDeps ++
    enumeratumDeps ++
    http4sDeps ++
    mainDeps ++
    testDeps

resolvers += Resolver.sonatypeRepo("snapshots")

addCompilerPlugin(
  ("org.typelevel" %% "kind-projector" % kindProjectorV).cross(CrossVersion.full)
)

enablePlugins(JavaAppPackaging, JDKPackagerPlugin, DockerPlugin)

dockerExposedPorts ++= Seq(8080)
dockerBaseImage := "openjdk:8-jre-alpine"
dockerCommands ++= Seq(
  Cmd("USER", "root"),
  ExecCmd("RUN", "apk", "add", "--no-cache", "bash")
)

mainClass in Compile := Some("org.github.eniqen.deduplicator.DeduplicatorApp")


conflictManager := ConflictManager.latestRevision