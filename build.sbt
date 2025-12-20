name := "earthquake-cooccurrence"

version := "1.0"

scalaVersion := "2.12.18"

// Dipendenze Spark
libraryDependencies ++= Seq(
  "org.apache.spark" %% "spark-core" % "3.5.0" % "provided",
  "org.apache.spark" %% "spark-sql" % "3.5.0" % "provided"
)

// Impostazioni per la creazione del JAR
assembly / assemblyMergeStrategy := {
  case PathList("META-INF", "services", xs @ _*) => MergeStrategy.concat
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case "application.conf" => MergeStrategy.concat
  case x if x.endsWith(".proto") => MergeStrategy.rename
  case x if x.contains("hadoop") => MergeStrategy.first
  case _ => MergeStrategy.first
}

// Escludi Scala library dal JAR (fornita da Spark)
assembly / assemblyOption := (assembly / assemblyOption).value
  .withIncludeScala(false)
  .withIncludeDependency(true)

// Main class
Compile / mainClass := Some("Main")

// Nome del JAR assembly
assembly / assemblyJarName := "earthquake-cooccurrence-assembly-1.0.jar"

// Opzioni del compilatore Scala
scalacOptions ++= Seq(
  "-deprecation",           // Avvisi su API deprecate
  "-feature",               // Avvisi su feature sperimentali
  "-unchecked",             // Avvisi su type erasure
  "-Xlint",                 // Linting aggiuntivo
  "-encoding", "UTF-8",     // Encoding sorgenti
  "-target:jvm-1.8"         // Target JVM 8
)

// Java version
javacOptions ++= Seq(
  "-source", "1.8",
  "-target", "1.8",
  "-encoding", "UTF-8"
)

// Evita conflitti con classi duplicate da Spark
assembly / assemblyShadeRules := Seq(
  ShadeRule.rename("com.google.common.**" -> "shaded.guava.@1").inAll,
  ShadeRule.rename("com.google.protobuf.**" -> "shaded.protobuf.@1").inAll
)

// Test configuration
Test / fork := true
Test / parallelExecution := false

// SBT options
Global / onChangedBuildSource := ReloadOnSourceChanges
