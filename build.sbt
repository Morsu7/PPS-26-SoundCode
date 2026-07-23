name := "PPS-26-SoundCode"

version := "1.0.0"

scalaVersion := "3.8.4"

// Definisce il progetto radice nella cartella corrente (".")
lazy val root = (project in file("."))
  .settings(
    // Puoi mettere altre impostazioni specifiche qui se serve
  )

libraryDependencies ++= Seq(
  "org.scalatest" %% "scalatest" % "3.2.18" % Test,
  "com.lihaoyi" %% "fastparse" % "3.1.1",

  "org.scalafx" %% "scalafx" % "21.0.0-R32",
  "org.fxmisc.richtext" % "richtextfx" % "0.11.7"
)

Compile / mainClass := Some("soundcode.main")

assembly / mainClass := Some("soundcode.main")
assembly / assemblyJarName := s"soundcode-${version.value}.jar"

assembly / assemblyMergeStrategy := {
  case "module-info.class" =>
    MergeStrategy.discard
  case PathList("META-INF", "substrate", "config", _ @ _*) =>
    // Metadati per GraalVM Native Image: non servono nell'esecuzione JVM.
    MergeStrategy.discard
  case path =>
    val defaultStrategy = (assembly / assemblyMergeStrategy).value
    defaultStrategy(path)
}
