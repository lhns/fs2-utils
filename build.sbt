lazy val scalaVersions = Seq("3.3.1", "2.13.11")

ThisBuild / scalaVersion := scalaVersions.head
ThisBuild / versionScheme := Some("early-semver")
ThisBuild / organization := "de.lhns"

val V = new {
  val fs2 = "3.7.0"
  val logbackClassic = "1.4.11"
  val munit = "0.7.29"
  val munitTaglessFinal = "0.2.0"
}

lazy val commonSettings: SettingsDefinition = Def.settings(
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
    Developer(id = "lhns", name = "Pierre Kisters", email = "pierrekisters@gmail.com", url = url("https://github.com/lhns/"))
  ),

  libraryDependencies ++= Seq(
    "ch.qos.logback" % "logback-classic" % V.logbackClassic % Test,
    "de.lolhens" %%% "munit-tagless-final" % V.munitTaglessFinal % Test,
    "org.scalameta" %%% "munit" % V.munit % Test,
  ),

  testFrameworks += new TestFramework("munit.Framework"),

  libraryDependencies ++= virtualAxes.?.value.getOrElse(Seq.empty).collectFirst {
    case VirtualAxis.ScalaVersionAxis(version, _) if version.startsWith("2.") =>
      compilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
  },

  Compile / doc / sources := Seq.empty,

  publishMavenStyle := true,

  publishTo := sonatypePublishToBundle.value,

  sonatypeCredentialHost := {
    if (sonatypeProfileName.value == "de.lolhens")
      "oss.sonatype.org"
    else
      "s01.oss.sonatype.org"
  },

  credentials ++= (for {
    username <- sys.env.get("SONATYPE_USERNAME")
    password <- sys.env.get("SONATYPE_PASSWORD")
  } yield Credentials(
    "Sonatype Nexus Repository Manager",
    sonatypeCredentialHost.value,
    username,
    password
  )).toList,

  pomExtra := {
    if (sonatypeProfileName.value == "de.lolhens")
      <distributionManagement>
        <relocation>
          <groupId>de.lhns</groupId>
        </relocation>
      </distributionManagement>
    else
      pomExtra.value
  }
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
      "co.fs2" %%% "fs2-core" % V.fs2,
    ),

    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
  )
  .jvmPlatform(scalaVersions)
  .jsPlatform(scalaVersions)

lazy val io = projectMatrix.in(file("io"))
  .dependsOn(core % "compile->compile;test->test")
  .settings(commonSettings)
  .settings(
    name := "fs2-io-utils",

    libraryDependencies ++= Seq(
      "co.fs2" %%% "fs2-io" % V.fs2,
    ),

    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },
  )
  .jvmPlatform(scalaVersions)
  .jsPlatform(scalaVersions)

lazy val sample = projectMatrix.in(file("sample"))
  .dependsOn(io)
  .settings(commonSettings)
  .settings(
    name := "fs2-utils-sample",

    libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-classic" % V.logbackClassic,
    ),

    publish / skip := true,
  )
  .jvmPlatform(Seq(scalaVersions.head))
