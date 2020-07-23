import sbt._

object Dependencies {

  object versionOf {
    val scala           = "2.13.3"
    val cats            = "2.1.1"
    val monocle         = "2.0.5"
    val magnolia        = "0.16.0"
    val zio             = "1.0.0-RC21-2"
    val zioConfig       = "1.0.0-RC24"
    val zioInteropCats  = "2.1.4.0-RC17"
    val zioLogging      = "0.3.2"
    val zioTestIntelliJ = "1.0.0-RC20"
    val diffx           = "0.3.29"
    val slf4j           = "1.7.30"
    val logbackClassic  = "1.2.3"
    val circe           = "0.13.0"
    val http4s          = "0.21.6"
    val poiTl           = "1.8.0"
    val scopt           = "4.0.0-RC2"
    val scalaTest       = "3.1.1"
    val newType         = "0.4.4"
  }

  val fp = Seq(
    "org.typelevel"              %% "cats-core"     % versionOf.cats,
    "com.github.julien-truffaut" %% "monocle-core"  % versionOf.monocle,
    "com.github.julien-truffaut" %% "monocle-macro" % versionOf.monocle
  )

  val meta = Seq(
    "com.softwaremill.diffx" %% "diffx-core"         % versionOf.diffx,
    "com.propensive"         %% "magnolia"           % versionOf.magnolia,
    "org.scala-lang"         % "scala-reflect"       % versionOf.scala % Provided, // For magnolia
    "io.estatico"            %% "newtype"            % versionOf.newType,
    "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.1" // TODO
  )

  val io = Seq(
    "dev.zio" %% "zio",
    "dev.zio" %% "zio-macros",
    "dev.zio" %% "zio-streams"
  ).map(_     % versionOf.zio) ++ Seq(
    "dev.zio" %% "zio-interop-cats" % versionOf.zioInteropCats
  )

  val logs = Seq(
    "org.slf4j"      % "slf4j-api"          % versionOf.slf4j,
    "ch.qos.logback" % "logback-classic"    % versionOf.logbackClassic,
    "dev.zio"        %% "zio-logging"       % versionOf.zioLogging,
    "dev.zio"        %% "zio-logging-slf4j" % versionOf.zioLogging
  )

  val config = Seq(
    "dev.zio" %% "zio-config",
    "dev.zio" %% "zio-config-magnolia",
    "dev.zio" %% "zio-config-typesafe"
  ).map(_ % versionOf.zioConfig)

  val serialization = Seq(
    "io.circe" %% "circe-core",
    "io.circe" %% "circe-generic",
    "io.circe" %% "circe-parser",
    "io.circe" %% "circe-generic-extras"
  ).map(_ % versionOf.circe)

  val language = Seq(
    "padeg" % "lib" % "3.3.0.24" from "https://dl.bintray.com/jbaruch/jbaruch-maven/padeg/lib/padeg/3.3.0.24/padeg-3.3.0.24.jar"
  )

  val http = Seq(
    "org.http4s" %% "http4s-blaze-server",
    "org.http4s" %% "http4s-blaze-client",
    "org.http4s" %% "http4s-dsl",
    "org.http4s" %% "http4s-circe"
  ).map(_        % versionOf.http4s) ++ Seq(
    "org.http4s" %% "rho-swagger" % "0.20.0"
  )

  val reports = Seq(
    "com.deepoove" % "poi-tl" % versionOf.poiTl
  )

  val cli = Seq(
    "com.github.scopt" %% "scopt" % versionOf.scopt
  )

  val tests = Seq(
    "dev.zio" %% "zio-test"          % versionOf.zio             % Test,
    "dev.zio" %% "zio-test-sbt"      % versionOf.zio             % Test,
    "dev.zio" %% "zio-test-magnolia" % versionOf.zio             % Test,
    "dev.zio" %% "zio-test-intellij" % versionOf.zioTestIntelliJ % Test
  )
}
