import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.12.1",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "bankTranser",
    libraryDependencies ++= Seq(
      scalaTest % Test,
      akkaHttp,
      jsonSupport,
      "org.scalaj" %% "scalaj-http" % "2.3.0" % Test
    )
  )
