import sbt._
import sbt.Keys._

object Version {
  val scala = "2.10.4"
  val spray = "1.2.1"
  val sprayJson = "1.2.6"
  val akka = "2.2.4"
  val config = "1.2.1"
  val lucene = "4.10.0"
  val jsoup = "1.7.3"
  val porter = "0.2.0"
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

  val logging = Seq(
    "org.slf4j" % "slf4j-api" % "1.7.6",
    "ch.qos.logback" % "logback-classic" % "1.1.2" % "runtime",
    "com.typesafe.akka" %% "akka-slf4j" % Version.akka % "runtime"
  )

  val spray = Seq(
    "io.spray" % "spray-client" % Version.spray,
    "io.spray" % "spray-routing" % Version.spray
  )

  val sprayJson = Seq(
    "io.spray" %% "spray-json" % Version.sprayJson
  )

  val porter = Seq(
    "org.eknet.porter" %% "porter-api" % Version.porter,
    "org.eknet.porter" %% "porter-app" % Version.porter
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
    "org.apache.logging.log4j" % "log4j-api" % "2.0-rc1",
    //instead of log4j-core, route to slf4j
    "org.apache.logging.log4j" % "log4j-to-slf4j" % "2.0-rc1"
  )

  val jsoup = Seq(
    "org.jsoup" % "jsoup" % Version.jsoup
  )

  val config = Seq(
    "com.typesafe" % "config" % Version.config
  )

  val lucene = Seq(
    "org.apache.lucene" % "lucene-core" % Version.lucene,
    "org.apache.lucene" % "lucene-analyzers-common" % Version.lucene,
    "org.apache.lucene" % "lucene-queryparser" % Version.lucene
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
      parallelExecution in Test := false,
      javaOptions ++= Seq("-Dconfig.file=src/main/dist/etc/sitebag.conf",
                          "-Dsitebag.dbname=sitebagplay",
                          "-Dsitebag.create-admin-account=true",
                          "-Dsitebag.always-save-document=true",
                          "-Dlogback.configurationFile=src/main/dist/etc/logback.xml"),
      fork in run := true,
      Twirl.twirlImports := Seq("org.eknet.sitebag.ui._", "org.eknet.sitebag.rest.EntrySearch", "org.eknet.sitebag.model._"),
      libraryDependencies ++= Deps.spray ++ Deps.sprayJson ++
        Deps.akka ++ Deps.porter ++ Deps.reactiveMongo ++ Deps.jsoup ++
        Deps.config ++ Deps.lucene ++ Deps.logging ++ Deps.testBasics
    )
  )

  lazy val buildTimeKey = BuildInfoKey.action("buildTime") {
    System.currentTimeMillis
  }
  lazy val gitRevKey = BuildInfoKey.action("revision") {
    Process("git rev-parse HEAD").lines.head
  }

  override lazy val settings = super.settings ++ Seq(
    version := "0.2.0",
    resolvers ++= Seq("spray repo" at "http://repo.spray.io",
                      "typesafe-releases" at "http://repo.typesafe.com/typesafe/releases",
                      "eknet-maven2" at "https://eknet.org/maven2"),
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
