import Dependencies._
//import sbt.Keys.{licenses, version}


scalaVersion := "2.12.6"

name := "consul-client"
organization := "org.dwrs.consul"
version := "0.1"

//libraryDependencies += "org.typelevel" %% "cats-core" % "1.2.0"
libraryDependencies ++= Seq(sttp, sttpMonix, akkaStreams, sttpJson, logback, scalaLogging, scalactic, scalaTest)

licenses += ("Mozilla-2.0", url("https://www.mozilla.org/en-US/MPL/2.0/"))
bintrayRepository := "scala-tools"