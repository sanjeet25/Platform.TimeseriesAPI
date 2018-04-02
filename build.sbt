name := "timeseries"
organization := "com.jci"
version := "1.0-SNAPSHOT"

lazy val root = Project("timeseries", file(".")).enablePlugins(PlayScala).disablePlugins(PlayLayoutPlugin)
scalaVersion := "2.12.4"
fork := true

libraryDependencies ++= Seq (
  "com.datastax.cassandra" % "cassandra-driver-core" % "3.4.0",
  "com.beachape" %% "enumeratum" % "1.5.13"
)
