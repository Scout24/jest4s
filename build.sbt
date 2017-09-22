import sbt.Keys._
import sbt._
import sbtrelease.ReleaseStateTransformations._

val scalaVersionString = "2.12.3"
val javaVersion = "1.8"
val encoding = "utf-8"
val playVersion = "2.6.3"
val specs2Version = "3.7.2"
val elasticSearchVersion: String = "5.3.0"

val testDependencies = Seq(
  "org.elasticsearch" % "elasticsearch" % elasticSearchVersion,
  "com.typesafe.akka" %% "akka-testkit" % "2.5.4",
  "com.typesafe.play" %% "play-test" % playVersion,
  "com.typesafe.play" %% "play-specs2" % playVersion,
  "org.specs2" %% "specs2-core" % specs2Version,
  "org.specs2" %% "specs2-junit" % specs2Version,
  "org.specs2" %% "specs2-mock" % specs2Version,
  "org.apache.logging.log4j" % "log4j-api" % "2.8.2",
  "org.apache.logging.log4j" % "log4j-core" % "2.8.2",
  "org.elasticsearch.plugin" % "transport-netty3-client" % "5.3.0"
).map(_ % "test")

val appDependencies = Seq(
  "net.maffoo" %% "jsonquote-core" % "0.5.1",
  "net.maffoo" %% "jsonquote-play" % "0.5.1",
  "com.typesafe.play" %% "play" % playVersion % "provided",
  "com.typesafe.play" %% "play-json" % playVersion % "provided",
  "io.searchbox" % "jest" % "5.3.2",
  "com.typesafe.akka" %% "akka-stream" % "2.5.4",
  "org.asynchttpclient" % "async-http-client" % "2.0.32"
)

lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

lazy val root = Project(
  id = "root",
  base = file("."))
  .settings(Defaults.coreDefaultSettings)
  .settings(
    name := "jest4s",
    organization := "de.is24",
    scalaVersion := scalaVersionString,
    crossScalaVersions := Seq(scalaVersionString, "2.11.11"),
    releaseCrossBuild := true,
    libraryDependencies ++= appDependencies ++ testDependencies,
    javacOptions ++= Seq("-source", javaVersion, "-target", javaVersion, "-Xlint"),
    scalacOptions ++= Seq("-feature", "-language:postfixOps", "-target:jvm-" + javaVersion, "-unchecked", "-deprecation", "-encoding", encoding),

    /**
      * scala style
      */
    compileScalastyle := scalastyle.in(Compile).toTask("").value,
    (compile in Compile) := ((compile in Compile) dependsOn compileScalastyle).value,

    resolvers ++= Seq(
      "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
      "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
    ),

    publishTo := {
      val nexus = "https://oss.sonatype.org/"
      if (isSnapshot.value)
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },

    /**
      * publish
      */
    pgpReadOnly := false,
    publishMavenStyle := true,
    pomExtra in Global := {
      <url>https://github.com/ImmobilienScout24/jest4s</url>
        <licenses>
          <license>
            <name>Apache 2</name>
            <url>http://www.apache.org/licenses/LICENSE-2.0.txt</url>
          </license>
        </licenses>
        <scm>
          <connection>scm:git:github.com/ImmobilienScout24/jest4s</connection>
          <developerConnection>scm:git:git@github.com:ImmobilienScout24/jest4s</developerConnection>
          <url>github.com/ImmobilienScout24/jest4s</url>
        </scm>
        <developers>
          <developer>
            <id>is24-mriehl</id>
            <name>Maximilien Riehl</name>
            <url>http://github.com/mriehl</url>
          </developer>
        </developers>
    },
    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies,
      inquireVersions,
      runTest,
      setReleaseVersion,
      commitReleaseVersion,
      tagRelease,
      releaseStepCommand("publishSigned"),
      releaseStepCommand("sonatypeRelease"),
      setNextVersion,
      commitNextVersion,
      pushChanges
    )
  )
