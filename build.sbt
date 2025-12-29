name := "earthquake-application"

version := "2.0"


scalaVersion := "2.13.12"


libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "3.3.0" % "provided",
  "org.apache.spark" %% "spark-sql"  % "3.3.0" % "provided",
  "log4j" % "log4j" % "1.2.17"
)

// Impostazioni per la creazione del JAR assembly
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat
  case PathList("META-INF", xs @ _*)            => MergeStrategy.discard
  case "reference.conf"                         => MergeStrategy.concat
  case "application.conf"                       => MergeStrategy.concat
  case x if x.endsWith(".proto")                => MergeStrategy.rename
  case x if x.contains("hadoop")                => MergeStrategy.first
  case _                                        => MergeStrategy.first
}


assembly / assemblyOption := (assembly / assemblyOption).value
  .withIncludeScala(false)
  .withIncludeDependency(false)

// Main class
Compile / mainClass := Some("Main")

// Nome del JAR assembly
assembly / assemblyJarName := "earthquake-application.jar"

// Opzioni del compilatore Scala
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-encoding", "UTF-8",
  "-release", "11"
)



javacOptions ++= Seq(
  "--release", "11",
  "-encoding", "UTF-8"
)

assembly / assemblyShadeRules := Seq(
  ShadeRule.rename("com.google.common.**"   -> "shaded.guava.@1").inAll,
  ShadeRule.rename("com.google.protobuf.**" -> "shaded.protobuf.@1").inAll
)

// Test configuration
Test / fork := true
Test / parallelExecution := false

// SBT options
Global / onChangedBuildSource := ReloadOnSourceChanges
