import sbt._
import sbt.Keys._

object Version {
  val scala = "2.10.3"
  val spray = "1.2.1"
  val akka = "2.2.4"
}

object Deps {
  val testBasics = Seq(
    "org.scalatest" %% "scalatest" % "2.1.0",
    "org.scalacheck" %% "scalacheck" % "1.11.3",
    "org.specs2" %% "specs2" % "2.3.10",
    "io.spray" % "spray-can" % Version.spray,
    "io.spray" % "spray-testkit" % Version.spray
  ) map (_ % "test")

  val akka = Seq(
    "com.typesafe.akka" %% "akka-actor" % Version.akka,
    "com.typesafe.akka" %% "akka-testkit" % Version.akka % "test"
  )

  val logback = Seq(
    "org.slf4j" % "slf4j-api" % "1.7.5",
    "ch.qos.logback" % "logback-classic" % "1.0.13" % "runtime",
    "com.typesafe.akka" %% "akka-slf4j" % Version.akka % "runtime"
  )

  val spray = Seq(
    "io.spray" % "spray-client" % Version.spray,
    "io.spray" % "spray-routing" % Version.spray
  )

  val sprayJson = Seq(
    "io.spray" %% "spray-json" % "1.2.5"
  )

  val porter = Seq(
    "org.eknet.porter" %% "porter-api" % "0.2.0-SNAPSHOT",
    "org.eknet.porter" %% "porter-app" % "0.2.0-SNAPSHOT"
  )

  val casbah = Seq(
    "org.mongodb" %% "casbah-commons" % "2.6.5",
    "org.mongodb" %% "casbah-query" % "2.6.5",
    "org.mongodb" %% "casbah-core" % "2.6.5"
  )

  val jsoup = Seq(
    "org.jsoup" % "jsoup" % "1.7.3"
  )

  val config = Seq(
    "com.typesafe" % "config" %"1.2.0"
  )
}

object Sitebag extends sbt.Build {
  import sbtbuildinfo.Plugin._
  
  lazy val module = Project(
    id = "sitebag",
    base = file("."),
    settings = Project.defaultSettings ++ buildInfoSettings ++ Seq(
      name := "sitebag",
      sourceGenerators in Compile <+= buildInfo,
      buildInfoKeys := Seq(name, version, scalaVersion, buildTimeKey, gitRevKey),
      buildInfoPackage := "org.eknet.sitebag",
      libraryDependencies ++= Deps.spray ++ Deps.sprayJson ++
        Deps.akka ++ Deps.porter ++ Deps.casbah ++ Deps.jsoup ++
        Deps.config ++ Deps.logback ++ Deps.testBasics
    )
  )

  lazy val buildTimeKey = BuildInfoKey.action("buildTime") {
    System.currentTimeMillis
  }
  lazy val gitRevKey = BuildInfoKey.action("revision") {
    Process("git rev-parse HEAD").lines.head
  }
  
  override lazy val settings = super.settings ++ Seq(
    version := "0.1.0-SNAPSHOT",
    resolvers ++= Seq("spray repo" at "http://repo.spray.io"),
    publishTo := Some("eknet-maven2" at "https://eknet.org/maven2"),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    pomIncludeRepository := { _ => false },
    organization := "org.eknet.sitebag",
    scalaVersion := Version.scala,
    scalacOptions := Seq("-unchecked", "-deprecation", "-feature"),
    publishMavenStyle := true,
    publishTo := Some("eknet-maven2" at "https://eknet.org/maven2"),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    pomIncludeRepository := (_ => false),
    licenses := Seq("ASL2" -> url("http://www.apache.org/LICENESE-2.0.txt"))
  )
}
