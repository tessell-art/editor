addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.20.2")
addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.6.0")
addSbtPlugin("ch.epfl.scala" % "sbt-scalafix" % "0.14.6")

// Plain library for the build definition (Scala.js JSDOM env)
libraryDependencies += "org.scala-js" %% "scalajs-env-jsdom-nodejs" % "1.0.0"
