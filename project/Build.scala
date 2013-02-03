import sbt._
import Keys._

object SynthBuild extends Build {
  lazy val SynthProject = Project("Synth", file(".")).settings(

    scalaVersion := "2.10.0",

    // scalacOptions += "-Ydependent-method-types",
    
    libraryDependencies ++= Seq(
      //"com.typesafe.akka" % "akka-actorh_2.10" % "2.1.0",
      //"net.databinder" %% "unfiltered-netty-server" % "0.6.4",
      //"net.databinder" %% "unfiltered-filter" % "0.6.4",
      //"net.databinder" %% "unfiltered-jetty" % "0.6.4",
      "org.scalaz" % "scalaz-core_2.10" % "7.0.0-M7",
      "org.scalaz" % "scalaz-effect_2.10" % "7.0.0-M7",
      //"com.orientechnologies" % "orient-commons" % "1.3.0",
      //"com.orientechnologies" % "orientdb-core" % "1.3.0",
      //"com.orientechnologies" % "orientdb-object" % "1.3.0",
      //"io.spray" % "spray-json_2.10" % "1.2.3",
      "com.chuusai" % "shapeless_2.10" % "1.2.3"
      //"org.scala-lang" % "scala-reflect" % "2.10.0",
      //"de.sciss" %% "scalacollider" % "1.3.+"
    ),
    resolvers ++= Seq(
      //"Sonatype groups/public" at "https://oss.sonatype.org/content/groups/public/",
      //"Versant db4o Repository" at "https://source.db4o.com/maven/",
      //"Spray repository" at "http://repo.spray.io/"
      "Sonatype OSS Releases" at "https://oss.sonatype.org/content/repositories/releases/",
      "Typesafe Repository" at "https://typesafe.artifactoryonline.com/typesafe/releases/"
    )
  )
}
