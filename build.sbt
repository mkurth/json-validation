import sbt.Keys.libraryDependencies

ThisBuild / version      := "0.1.0"
ThisBuild / scalaVersion := "3.1.3"

lazy val domain = (project in file("domain"))
  .settings(
    name                := "json-schema-validator",
    libraryDependencies += "org.typelevel"             %% "cats-effect"           % "3.3.14",
    libraryDependencies += "org.scalatest"             %% "scalatest"             % "3.2.13" % "test",
    libraryDependencies += "io.circe"                  %% "circe-parser"          % "0.14.2",
    libraryDependencies += "com.github.java-json-tools" % "json-schema-validator" % "2.2.14",
    scalacOptions ++= List(
      "-Xfatal-warnings"
    )
  )

val tapirVersion = "1.0.4"
lazy val app     = (project in file("app"))
  .settings(
    name                := "json-schema-validator-app",
    libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-http4s-server"     % tapirVersion,
    libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-core"              % tapirVersion,
    libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-json-circe"        % tapirVersion,
    libraryDependencies += "com.softwaremill.sttp.tapir" %% "tapir-swagger-ui-bundle" % tapirVersion,
    libraryDependencies += "org.http4s"                  %% "http4s-blaze-server"     % "0.23.12",
    libraryDependencies += "dev.profunktor"              %% "redis4cats-effects"      % "1.2.0",
    libraryDependencies += "ch.qos.logback"               % "logback-classic"         % "1.2.11",
    dockerExposedPorts  := List(8080)
  )
  .enablePlugins(JavaAppPackaging)
  .dependsOn(domain)
