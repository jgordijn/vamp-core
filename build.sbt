import sbt.Keys._

organization in ThisBuild := "io.vamp"

name := """core"""

version in ThisBuild := "0.7.7"// + GitHelper.headSha()

scalaVersion := "2.11.6"

scalaVersion in ThisBuild := scalaVersion.value

publishMavenStyle := true

// This has to be overridden for sub-modules to have different description
description in ThisBuild:= """Core is the brain of Vamp."""

pomExtra in ThisBuild := <url>http://vamp.io</url>
    <licenses>
      <license>
        <name>The Apache License, Version 2.0</name>
        <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
      </license>
    </licenses>
    <developers>
      <developer>
        <name>Dragoslav Pavkovic</name>
        <email>drago@magnetic.io</email>
        <organization>VAMP</organization>
        <organizationUrl>http://vamp.io</organizationUrl>
      </developer>
      <developer>
        <name>Matthijs Dekker</name>
        <email>matthijs@magnetic.io</email>
        <organization>VAMP</organization>
        <organizationUrl>http://vamp.io</organizationUrl>
      </developer>
    </developers>
    <scm>
      <connection>scm:git:git@github.com:magneticio/vamp-core.git</connection>
      <developerConnection>scm:git:git@github.com:magneticio/vamp-core.git</developerConnection>
      <url>git@github.com:magneticio/vamp-core.git</url>
    </scm>


// Use local maven repository
resolvers in ThisBuild ++= Seq(
  Resolver.typesafeRepo("releases"),
  Resolver.jcenterRepo
)

lazy val bintraySetting = Seq(
  bintrayOrganization  := Some("magnetic-io"),
    licenses  += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0.html")),
    bintrayRepository  := "vamp"
)


// Library Versions

val vampCommonVersion = "0.7.7"
val vampPulseVersion = "0.7.7"

val sprayVersion = "1.3.2"
//val sprayJsonVersion = "1.3.1"
val json4sVersion = "3.2.11"
val akkaVersion = "2.3.11"
val scalaLoggingVersion = "3.1.0"
val slf4jVersion = "1.7.10"
val logbackVersion = "1.1.2"
val junitVersion = "4.11"
val scalatestVersion = "2.2.4"
val tugboatVersion = "0.2.3"
val typesafeConfigVersion = "1.2.1"
val scalaAsyncVersion = "0.9.2"
val snakeYamlVersion = "1.14"
val h2Version = "1.3.166"
val slickVersion = "2.1.0"
val activeSlickVersion = "0.2.2"
val postgresVersion = "9.1-901.jdbc4"


val akkaTestkit = "com.typesafe.akka" %% "akka-testkit" % akkaVersion
val scalaTest = "org.scalatest" %% "scalatest" % scalatestVersion


// Force scala version for the dependencies
dependencyOverrides in ThisBuild ++= Set(
  "org.scala-lang" % "scala-compiler" % scalaVersion.value,
  "org.scala-lang" % "scala-library" % scalaVersion.value
)

// Root project and subproject definitions
lazy val root = project.in(file(".")).settings(bintraySetting: _*).settings(
  // Disable publishing root empty pom
  packagedArtifacts in file(".") := Map.empty,
  // allows running main classes from subprojects
  run := {
    (run in bootstrap in Compile).evaluated
  }
).aggregate(
  persistence, model, operation, bootstrap, container_driver, dictionary, pulse_driver, rest_api, router_driver, swagger, cli
).disablePlugins(sbtassembly.AssemblyPlugin)


lazy val bootstrap = project.settings(bintraySetting: _*).settings(
  description := "Bootstrap for Vamp Core",
  name:="core-bootstrap",
  libraryDependencies ++= Seq(
    "org.json4s" %% "json4s-native" % json4sVersion,
    "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "ch.qos.logback" % "logback-classic" % logbackVersion,
    "com.typesafe" % "config" % typesafeConfigVersion
   ),
  // Runnable assembly jar lives in bootstrap/target/scala_2.11/ and is renamed to core assembly for consistent filename for
  // downloading
  assemblyJarName in assembly := s"core-assembly-${version.value}.jar"
).dependsOn(rest_api)

lazy val rest_api = project.settings(bintraySetting: _*).settings(
  description := "REST api for Vamp Core",
  name:="core-rest_api",
  libraryDependencies ++=Seq(
    "io.spray" %% "spray-can" % sprayVersion,
    "io.spray" %% "spray-routing" % sprayVersion,
    "io.spray" %% "spray-httpx" % sprayVersion
  )
).dependsOn(operation, swagger).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val operation = project.settings(bintraySetting: _*).settings(
  description := "The control center of Vamp",
  name:="core-operation"
  ).dependsOn(persistence, container_driver, dictionary, pulse_driver).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val pulse_driver = project.settings(bintraySetting: _*).settings(
  description := "Enables Vamp to talk to Vamp Pulse",
  name:="core-pulse_driver",
  libraryDependencies ++=Seq(
    "io.vamp" %% "pulse-client" % vampPulseVersion
  )
).dependsOn(router_driver).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val router_driver = project.settings(bintraySetting: _*).settings(
  description := "Enables Vamp to talk to Vamp Router",
  name:="core-router_driver",
  libraryDependencies ++= Seq(
    scalaTest % "test",
    akkaTestkit % "test"
  )
).dependsOn(model).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val container_driver = project.settings(bintraySetting: _*).settings(
  description := "Enables Vamp to talk to container managers",
  name:="core-container_driver",
  libraryDependencies ++=Seq(
    "org.scala-lang.modules" %% "scala-async" % scalaAsyncVersion,
    "io.vamp" %% "tugboat" % tugboatVersion exclude("org.slf4j", "slf4j-log4j12")
  )
).dependsOn(model).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val persistence = project.settings(bintraySetting: _*).settings(
  description:= "Stores Vamp artifacts",
  name:="core-persistence",
  libraryDependencies ++=Seq(
    "io.vamp" %% "pulse-client" % vampPulseVersion,
    "com.h2database" % "h2" % h2Version,
    "com.typesafe.slick" %% "slick" % slickVersion,
    "io.strongtyped" %% "active-slick" % activeSlickVersion,
    "postgresql" % "postgresql" % postgresVersion,
    "junit" % "junit" % junitVersion % "test",
    "org.scalatest" %% "scalatest" % scalatestVersion % "test"
  )
).dependsOn(model).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val cli = project.settings(bintraySetting: _*).settings(
  description := "Command Line Interface for Vamp",
  name:="core-cli",
  libraryDependencies ++= Seq(
    "org.slf4j" % "slf4j-api" % slf4jVersion,
    "ch.qos.logback" % "logback-classic" % logbackVersion
  ),
  assemblyJarName in assembly := s"vamp-cli-${version.value}.jar"
).dependsOn(model, rest_api)

lazy val dictionary = project.settings(bintraySetting: _*).settings(
  description := "Dictionary for Vamp",
  name:="core-dictionary"
  ).dependsOn(model).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val model = project.settings(bintraySetting: _*).settings(
  description := "Definitions of Vamp artifacts",
  name:="core-model",
  libraryDependencies ++= Seq(
    "io.vamp" %% "common" % vampCommonVersion,
    "io.vamp" %% "pulse-model" % vampPulseVersion,
    "org.yaml" % "snakeyaml" % snakeYamlVersion,
    "junit" % "junit" % junitVersion % "test",
    "org.scalatest" %% "scalatest" % scalatestVersion % "test"
  )
).disablePlugins(sbtassembly.AssemblyPlugin)

lazy val swagger = project.settings(bintraySetting: _*).settings(
  description := "Swagger annotations",
  name:="core-swagger"
).disablePlugins(sbtassembly.AssemblyPlugin)

// Java version and encoding requirements
scalacOptions += "-target:jvm-1.8"

javacOptions ++= Seq("-encoding", "UTF-8")

scalacOptions in ThisBuild ++= Seq(Opts.compile.deprecation, Opts.compile.unchecked) ++
  Seq("-Ywarn-unused-import", "-Ywarn-unused", "-Xlint", "-feature")




