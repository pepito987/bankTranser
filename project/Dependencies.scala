import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest" %% "scalatest" % "3.0.1"
  lazy val akkaHttp = "com.typesafe.akka" %% "akka-http" % "10.0.10"
  lazy val jsonSupport = "com.typesafe.akka" %% "akka-http-spray-json" % "10.0.10"
}
