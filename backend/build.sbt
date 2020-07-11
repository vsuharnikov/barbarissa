import VersionSourcePlugin.V

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full)
enablePlugins(VersionSourcePlugin, JavaAppPackaging)

name := "backend"
V.scalaPackage := "com.github.vsuharnikov.barbarissa.backend"

libraryDependencies ++= {
  import Dependencies._
  fp ++ meta ++ io ++ logs ++ config ++ serialization ++ http ++ reports ++ cli ++ tests
}

// Run
run / fork := true
run / cancelable := true

// Packaging
mainClass := Some("com.github.vsuharnikov.barbarissa.backend.Main")
mappings in (Compile, packageDoc) := Seq.empty
topLevelDirectory := Some(name.value)
bashScriptConfigLocation := Some("${app_home}/../conf/barbarissa-backend/application.ini")

inConfig(Universal)(Seq(
  packageName := name.value, // An archive file name
  //  // To not override configs
  //  javaOptions ++= Seq(
  //    "-Xmx2g",
  //    "-Xms128m",
  //  ).map(x => s"-J$x")
))

// Tests
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")