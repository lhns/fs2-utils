lazy val scalaVersions = Seq("2.13.6", "2.12.16")

ThisBuild / scalaVersion := scalaVersions.head
ThisBuild / versionScheme := Some("early-semver")

lazy val commonSettings: SettingsDefinition = Def.settings(
  organization := "de.lolhens",
  version := {
    val Tag = "refs/tags/(.*)".r
    sys.env.get("CI_VERSION").collect { case Tag(tag) => tag }
      .getOrElse("0.0.1-SNAPSHOT")
  },

  licenses += ("Apache-2.0", url("https://www.apache.org/licenses/LICENSE-2.0")),

  homepage := Some(url("https://github.com/LolHens/fs2-utils")),
  scmInfo := Some(
    ScmInfo(
      url("https://github.com/LolHens/fs2-utils"),
      "scm:git@github.com:LolHens/fs2-utils.git"
    )
  ),
  developers := List(
    Developer(id = "LolHens", name = "Pierre Kisters", email = "pierrekisters@gmail.com", url = url("https://github.com/LolHens/"))
  ),

  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % "1.2.11" % Test,
    "de.lolhens" %%% "munit-tagless-final" % "0.2.0" % Test,
    "org.scalameta" %%% "munit" % "0.7.26" % Test,
  ),

  testFrameworks += new TestFramework("munit.Framework"),

  addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1"),

  Compile / doc / sources := Seq.empty,

  publishMavenStyle := true,

  publishTo := sonatypePublishToBundle.value,

  credentials ++= (for {
    username <- sys.env.get("SONATYPE_USERNAME")
    password <- sys.env.get("SONATYPE_PASSWORD")
  } yield Credentials(
    "Sonatype Nexus Repository Manager",
    "oss.sonatype.org",
    username,
    password
  )).toList
)

name := (core.projectRefs.head / name).value

lazy val root: Project =
  project
    .in(file("."))
    .settings(commonSettings)
    .settings(
      publishArtifact := false,
      publish / skip := true
    )
    .aggregate(core.projectRefs: _*)
    .aggregate(io.projectRefs: _*)
    .aggregate(sample.projectRefs: _*)

lazy val core = projectMatrix.in(file("core"))
  .settings(commonSettings)
  .settings(
    name := "fs2-utils",

    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-core" % "3.0.2",
    ),
  )
  .jvmPlatform(scalaVersions)
  .jsPlatform(scalaVersions)

lazy val io = projectMatrix.in(file("io"))
  .settings(commonSettings)
  .settings(
    name := "fs2-io-utils",

    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-io" % "3.0.2",
    ),
  )
  .dependsOn(core)
  .jvmPlatform(scalaVersions)

lazy val sample = projectMatrix.in(file("sample"))
  .settings(commonSettings)
  .settings(
    name := "fs2-utils-sample",

    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % "1.2.11",
    ),

    publish / skip := true,
  )
  .dependsOn(io)
  .jvmPlatform(Seq(scalaVersions.head))
