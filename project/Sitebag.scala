import sbt._
import sbt.Keys._

object Version {
  val scala = "2.10.4"
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
    "com.typesafe.akka" %% "akka-remote" % Version.akka,
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
    "io.spray" %% "spray-json" % "1.2.6"
  )

  val porter = Seq(
    "org.eknet.porter" %% "porter-api" % "0.2.0",
    "org.eknet.porter" %% "porter-app" % "0.2.0"
  )

  val reactiveMongo = Seq(
    //use a new version of log4j2, ran into this: https://issues.apache.org/jira/browse/LOG4J2-477
    //also remove the 14mb dependency scala-compiler, which is not needed in sitebag
    "org.reactivemongo" %% "reactivemongo" % "0.10.0" excludeAll (
      ExclusionRule("org.apache.logging.log4j", "log4j-api"),
      ExclusionRule("org.apache.logging.log4j", "log4j-core"),
      ExclusionRule("org.scala-lang", "scala-compiler"),
      ExclusionRule("org.scala-lang", "scala-reflect")
    ),
    "org.reactivemongo" %% "reactivemongo-bson" % "0.10.0" intransitive(),
    "org.apache.logging.log4j" % "log4j-core" % "2.0-rc1",
    "org.apache.logging.log4j" % "log4j-api" % "2.0-rc1"
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
  import twirl.sbt.TwirlPlugin._

  lazy val module = Project(
    id = "sitebag",
    base = file("."),
    settings = Project.defaultSettings ++ buildInfoSettings ++ Twirl.settings ++ Distribution.distSettings ++ Seq(
      name := "sitebag",
      sourceGenerators in Compile <+= buildInfo,
      buildInfoKeys := Seq(name, version, scalaVersion, buildTimeKey, gitRevKey),
      buildInfoPackage := "org.eknet.sitebag",
      Twirl.twirlImports := Seq("org.eknet.sitebag.ui._", "org.eknet.sitebag.rest.EntrySearch", "org.eknet.sitebag.model._"),
      libraryDependencies ++= Deps.spray ++ Deps.sprayJson ++
        Deps.akka ++ Deps.porter ++ Deps.reactiveMongo ++ Deps.jsoup ++
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
    version := "0.1.2",
    resolvers ++= Seq("spray repo" at "http://repo.spray.io", "typesafe-releases" at "http://repo.typesafe.com/typesafe/releases", "eknet-maven2" at "https://eknet.org/maven2"),
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
