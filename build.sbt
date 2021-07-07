name := """try-cb-scala"""
organization := "com.couchbase"

version := "1.0-SNAPSHOT"

lazy val root = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.14"

libraryDependencies += guice
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
libraryDependencies += "com.couchbase.client" %% "scala-client" % "1.1.6"
libraryDependencies += "io.jsonwebtoken" % "jjwt" % "0.6.0"
libraryDependencies += "com.github.t3hnar" %% "scala-bcrypt" % "4.3.0"

// Used by sbt-assembly plugin to merge duplicate files
assemblyMergeStrategy in assembly := {
  case r if r.startsWith("reference.conf") => MergeStrategy.concat
  case PathList("META-INF", m) if m.equalsIgnoreCase("MANIFEST.MF") => MergeStrategy.discard
  case x => MergeStrategy.first
}
