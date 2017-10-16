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
      "log4j" % "log4j" % "1.2.14",
      "org.slf4j" % "slf4j-log4j12" % "1.7.21",
      "com.github.nscala-time" %% "nscala-time" % "2.16.0",
      "org.scalaj" %% "scalaj-http" % "2.3.0" % Test
    )
  )

parallelExecution in Test := false