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
  case PathList("META-INF", xs @ _*) => MergeStrategy.discard
  case "reference.conf" => MergeStrategy.concat
  case x => MergeStrategy.first
}

// Main class
Compile / mainClass := Some("Main")

// Nome del JAR assembly
assembly / assemblyJarName := "earthquake-cooccurrence-assembly-1.0.jar"

// Opzioni del compilatore Scala
scalacOptions ++= Seq(
  "-deprecation",
  "-feature",
  "-unchecked",
  "-Xlint",
  "-encoding", "UTF-8"
)

// Java version
javacOptions ++= Seq("-source", "1.8", "-target", "1.8")

// Evita conflitti con classi duplicate
assembly / assemblyShadeRules := Seq(
  ShadeRule.rename("com.google.common.**" -> "shaded.guava.@1").inAll
)

// Test configuration (opzionale)
Test / fork := true
Test / parallelExecution := false