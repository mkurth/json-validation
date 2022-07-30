import sbt.Keys.libraryDependencies

ThisBuild / version      := "0.1.0"
ThisBuild / scalaVersion := "2.13.8"

lazy val domain = (project in file("domain"))
  .settings(
    name                := "json-schema-validator",
    libraryDependencies += "org.typelevel"             %% "cats-effect"           % "3.3.14",
    libraryDependencies += "org.scalatest"             %% "scalatest"             % "3.2.12" % "test",
    libraryDependencies += "io.circe"                  %% "circe-parser"          % "0.14.2",
    libraryDependencies += "com.github.java-json-tools" % "json-schema-validator" % "2.2.14"
  )

lazy val app = (project in file("app"))
  .settings(
    name := "json-schema-validator-app"
  )
  .enablePlugins(JavaAppPackaging)
