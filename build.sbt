name := "datastore-scala-wrapper"

organization := "io.applicative"

version := "1.0-rc11"

scalaVersion := "2.12.8"

licenses += ("Apache-2.0", url(
  "https://www.apache.org/licenses/LICENSE-2.0.html"
))

crossScalaVersions := Seq("2.11.11", scalaVersion.value, "2.13.0")

// Publish settings for Maven Central
publishMavenStyle := true
pomExtra := (<url>https://github.com/applctv/gcp-scala-datastore/</url>
    <scm>
      <url>git@github.com:applctv/gcp-scala-datastore.git</url>
      <connection>scm:git:git@github.com:applctv/gcp-scala-datastore.git</connection>
    </scm>
    <developers>
      <developer>
        <id>applctv</id>
        <name>Applicative</name>
        <url>http://applicative.io</url>
      </developer>
      <developer>
        <id>a-panchenko</id>
        <name>Oleksandr Panchenko</name>
      </developer>
    </developers>)

libraryDependencies ++= {
  val gcdJavaSDKVersion = "1.82.0"
  val specsVersion = "4.6.0"
  val catsVersion = "1.3.1"

  Seq(
    "org.scala-lang" % "scala-reflect" % scalaVersion.value,
    ("com.google.cloud" % "google-cloud-datastore" % gcdJavaSDKVersion).excludeAll(ExclusionRule(organization = "com.google.guava", name = "guava")),
    "org.typelevel" %% "cats-effect" % catsVersion,
    "org.specs2" %% "specs2-core" % specsVersion % "test",
    "org.specs2" %% "specs2-mock" % specsVersion % "test"
  )
}
