name := "earthquake-cooccurrence"

version := "2.0"


scalaVersion := "2.12.18"

// Dipendenze Spark (allineate a Spark 4.0.1)
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

// ⚠️ IMPORTANTE:
// NON includere scala-library né dipendenze Spark nel fat JAR
// Spark fornisce già tutto a runtime
assembly / assemblyOption := (assembly / assemblyOption).value
  .withIncludeScala(true)
  .withIncludeDependency(true)

// Main class
Compile / mainClass := Some("Main")

// Nome del JAR assembly
assembly / assemblyJarName := "earthquake-cooccurrence-assembly-1.0.jar"

// Opzioni del compilatore Scala
scalacOptions ++= Seq(
  "-deprecation",        // Avvisi su API deprecate
  "-feature",            // Avvisi su feature sperimentali
  "-unchecked",          // Avvisi su type erasure
  "-Xlint",              // Linting aggiuntivo
  "-encoding", "UTF-8",
  "-release", "11"
)

// Opzioni compilatore Java
javacOptions ++= Seq(
  "--release", "11",
  "-encoding", "UTF-8"
)

// Evita conflitti con classi duplicate (lasciato invariato, non dannoso)
assembly / assemblyShadeRules := Seq(
  ShadeRule.rename("com.google.common.**"   -> "shaded.guava.@1").inAll,
  ShadeRule.rename("com.google.protobuf.**" -> "shaded.protobuf.@1").inAll
)

// Test configuration
Test / fork := true
Test / parallelExecution := false

// SBT options
Global / onChangedBuildSource := ReloadOnSourceChanges
