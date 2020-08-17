name := "barbarissa"

lazy val backend = project
lazy val root = (project in file(".")).aggregate(backend)

inScope(Global)(Seq(
  maintainer := "arz.freezy@gmail.com",
  version := "0.0.1",

  scalaVersion := Dependencies.versionOf.scala,
  scalacOptions ++= Seq(
    "-deprecation",
    "-Ymacro-annotations",
    "-language:higherKinds",
    "-Ybackend-parallelism", "4"
  )
))

addCompilerPlugin("com.olegpy" %% "better-monadic-for" % "0.3.1")
