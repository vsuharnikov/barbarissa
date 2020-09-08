Seq(
  "com.typesafe.sbt" % "sbt-native-packager" % "1.7.1",
  // sbt "dependencyUpdates"
  "com.timushev.sbt" % "sbt-updates" % "0.5.1",
  // https://timushev.com/posts/2020/07/25/life-with-fatal-warnings/
  "com.timushev.sbt" % "sbt-rewarn" % "0.1.0",
  // docker
  "se.marcuslonnberg" % "sbt-docker" % "1.7.0",
  // sbt "shield"
  // "dev.zio" % "zio-shield" % "0.1.0",
  "com.typesafe.sbt" % "sbt-git" % "1.0.0"
).map(addSbtPlugin)

// https://github.com/sbt/sbt-git#known-issues
libraryDependencies += "org.slf4j" % "slf4j-nop" % "1.7.21"
