import sbt._

object Dependencies {
  val sttp = "com.softwaremill.sttp" %% "core" % "1.1.+"
  val sttpAkka = "com.softwaremill.sttp" %% "akka-http-backend" % "1.1.+"
  val sttpMonix = "com.softwaremill.sttp" %% "async-http-client-backend-monix" % "1.1.+"
  val akkaStreams = "com.typesafe.akka" %% "akka-stream" % "2.5.+"
  val sttpJson = "com.softwaremill.sttp" %% "json4s" % "1.1.+"
  val logback = "ch.qos.logback" % "logback-classic" % "1.2.3"
  val scalaLogging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.0"
  val scalactic = "org.scalactic" %% "scalactic" % "3.0.5"
  val scalaTest = "org.scalatest" %% "scalatest" % "3.0.5" % Test
}
