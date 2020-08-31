import VersionSourcePlugin.V

addCompilerPlugin("org.typelevel" %% "kind-projector" % "0.11.0" cross CrossVersion.full)
enablePlugins(VersionSourcePlugin, JavaAppPackaging, sbtdocker.DockerPlugin)

val fullName = "barbarissa-backend"
name := "backend"

V.scalaPackage := "com.github.vsuharnikov.barbarissa.backend"

libraryDependencies ++= {
  import Dependencies._
  fp ++ meta ++ io ++ logs ++ config ++ serialization ++ db ++ csv ++ http ++ reports ++ language ++ cli ++ tests ++ Seq(
    "com.microsoft.ews-java-api" % "ews-java-api" % "2.0",
    // java.lang.ClassNotFoundException: javax.xml.ws.http.HTTPException
    "jakarta.xml.ws" % "jakarta.xml.ws-api" % "2.3.3"
  )
}

// Run
run / fork := true
run / cancelable := true

executableScriptName := fullName

// Packaging
mainClass := Some("com.github.vsuharnikov.barbarissa.backend.BarbarissaMain")
mappings in (Compile, packageDoc) := Seq.empty
topLevelDirectory := Some(fullName)
bashScriptConfigLocation := Some(s"$${app_home}/../conf/$fullName/application.ini")
Universal / packageBin / mappings ~= {
  _.filterNot { case (_, name) => name.endsWith("local.conf") }
}

inConfig(Universal)(
  Seq(
    packageName := s"$fullName-${version.value}" // An archive file name
    //  // To not override configs
    //  javaOptions ++= Seq(
    //    "-Xmx2g",
    //    "-Xms128m",
    //  ).map(x => s"-J$x")
  ))

// Docker
inTask(docker)(
  Seq(
    imageNames := List("latest", version.value).map { v =>
      ImageName(s"vsuharnikov/$fullName:$v")
    },
    dockerfile := new Dockerfile {
      val (user, userId)   = ("barbarissa", "112")
      val (group, groupId) = (user, "113")

      val userPath = s"/var/lib/$user"
      val appPath  = s"/usr/share/$user"

      val entryPointSh = s"$appPath/bin/$fullName"

      from("openjdk:11.0.8-jre-slim-buster")

      List(
        (Universal / stage).value -> s"$appPath/"
      ).foreach {
        case (source, destination) =>
          add(source = source, destination = destination, chown = s"$userId:$groupId")
      }

      runRaw(s"""mkdir -p $appPath $userPath/db && \\
groupadd -g $groupId $group && \\
useradd -d $userPath -g $groupId -u $userId -s /bin/bash -M $user && \\
chown -R $userId:$groupId $appPath $userPath && \\
chmod -R 755 $userPath $userPath""")

      user(s"$userId:$groupId")

      runShell("chmod", "+x", s"$appPath/bin/$fullName")
      workDir(userPath)
      entryPoint(
        entryPointSh,
        s"-Dbarbarissa.backend.runtime-dir=$userPath",
        s"-Dconfig.file=$userPath/main.conf"
      )
      volume(userPath)
      expose(10203)
    },
    buildOptions := BuildOptions(
      removeIntermediateContainers = BuildOptions.Remove.OnSuccess,
      pullBaseImage = BuildOptions.Pull.Always
    )
  )
)

// Tests
testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
