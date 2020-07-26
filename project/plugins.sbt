Seq(
  "com.typesafe.sbt" % "sbt-native-packager" % "1.7.1",
  // sbt "dependencyUpdates"
  "com.timushev.sbt" % "sbt-updates" % "0.5.1",
  // https://timushev.com/posts/2020/07/25/life-with-fatal-warnings/
  "com.timushev.sbt" % "sbt-rewarn" % "0.1.0"
).map(addSbtPlugin)
