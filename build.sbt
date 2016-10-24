import sbt.Keys._
import sbt._
import sbtrelease.ReleaseStateTransformations._
import com.typesafe.sbt.SbtScalariform._
import scalariform.formatter.preferences._

val javaVersion = "1.8"
val encoding = "utf-8"
val playVersion = "2.5.8"
val specs2Version = "3.7.2"
val elasticSearchVersion: String = "2.3.0"

val testDependencies = Seq(
  "org.elasticsearch" % "elasticsearch" % elasticSearchVersion,
  "com.typesafe.akka" %% "akka-testkit" % "2.3.9",
  "com.typesafe.play" %% "play-test" % playVersion,
  "com.typesafe.play" %% "play-specs2" % playVersion,
  "org.specs2" %% "specs2-core" % specs2Version,
  "org.specs2" %% "specs2-junit" % specs2Version,
  "org.specs2" %% "specs2-mock" % specs2Version
).map(_ % "test")

val appDependencies = Seq(
  "net.maffoo" %% "jsonquote-core" % "0.4.0",
  "net.maffoo" %% "jsonquote-play" % "0.4.0",
  "com.typesafe.play" %% "play" % playVersion % "provided",
  "com.typesafe.play" %% "play-json" % playVersion % "provided",
  "io.searchbox" % "jest" % "2.0.3",
  "com.typesafe.akka" %% "akka-stream" % "2.4.3",
  "org.asynchttpclient" % "async-http-client" % "2.0.2"
)

lazy val compileScalastyle = taskKey[Unit]("compileScalastyle")

lazy val root = Project(
  id = "root",
  base = file("."),
  settings = Defaults.coreDefaultSettings ++ net.virtualvoid.sbt.graph.Plugin.graphSettings ++ defaultScalariformSettings)
  .settings(
    name := "jest4s",
    organization := "de.is24",
    scalaVersion := "2.11.7",
    ivyScala := ivyScala.value map {
      _.copy(overrideScalaVersion = true)
    },
    libraryDependencies ++= appDependencies ++ testDependencies,
    javacOptions ++= Seq("-source", javaVersion, "-target", javaVersion, "-Xlint"),
    scalacOptions ++= Seq("-feature", "-language:postfixOps", "-target:jvm-" + javaVersion, "-unchecked", "-deprecation", "-encoding", encoding),
    compileScalastyle := org.scalastyle.sbt.ScalastylePlugin.scalastyle.in(Compile).toTask("").value,
    (compile in Compile) <<= (compile in Compile) dependsOn compileScalastyle,
    resolvers ++= Seq(
      "Typesafe repository" at "http://repo.typesafe.com/typesafe/releases/",
      "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases"
    ),
    publishTo <<= version { (v: String) =>
      val nexus = "https://oss.sonatype.org/"
      if (v.trim.endsWith("SNAPSHOT"))
        Some("snapshots" at nexus + "content/repositories/snapshots")
      else
        Some("releases"  at nexus + "service/local/staging/deploy/maven2")
    },

    /**
      * scalariform
      */
    ScalariformKeys.preferences := ScalariformKeys.preferences.value
      .setPreference(RewriteArrowSymbols, false)
      .setPreference(AlignArguments, true)
      .setPreference(DoubleIndentClassDeclaration, true)
      .setPreference(AlignSingleLineCaseStatements, true)
      .setPreference(SpacesAroundMultiImports, true)
      .setPreference(AlignParameters, true),

    /**
      * publish
      */
    pgpReadOnly := false,
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
