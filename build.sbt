import org.scalajs.linker.interface.ModuleSplitStyle

// Enable semanticdb for Scalafix (Scala 3)
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

// Optional: format when compiling (can be noisy in PRs; turn off if you prefer manual runs)
ThisBuild / scalafmtOnCompile := true

lazy val editor = project.in(file("."))
  .enablePlugins(ScalaJSPlugin) // Enable the Scala.js plugin in this project
  .settings(
    scalaVersion := "3.8.1",
    version := "0.3.3",
    name := "Tessella Editor",

    // Tell Scala.js that this is an application with a main method
    scalaJSUseMainModuleInitializer := true,

    /* Configure Scala.js to emit modules in the optimal way to
     * connect to Vite's incremental reload.
     * - emit ECMAScript modules
     * - emit as many small modules as possible for classes in the "livechart" package
     * - emit as few (large) modules as possible for all other classes
     *   (in particular, for the standard library)
     */
    scalaJSLinkerConfig ~= {
      _.withModuleKind(ModuleKind.ESModule)
        .withModuleSplitStyle(
          ModuleSplitStyle.SmallModulesFor(List("editor")))
    },

    /* Depend on the scalajs-dom library.
     * It provides static types for the browser DOM APIs.
     */
    libraryDependencies += "org.scala-js" %%% "scalajs-dom" % "2.8.1",
    libraryDependencies += "io.github.nguyenyou" %%% "ui5-webcomponents-laminar" % "2.14.0",
    libraryDependencies += "com.raquo" %%% "laminar" % "17.2.1",
    libraryDependencies += "io.github.iltotore" %%% "iron" % "3.2.3",
    libraryDependencies += "org.typelevel" %%% "spire" % "0.18.0",
    libraryDependencies += "io.github.scala-tessella" %%% "ring-seq" % "0.6.2+52-9bf40159",
    libraryDependencies += "io.github.scala-tessella" %%% "dcel" % "0.1.0-SNAPSHOT",

    // Test dependencies
    libraryDependencies += "org.scalameta" %%% "munit" % "1.0.0" % Test,

    // Compiler hygiene: turn on key warnings and make them fail the build
    scalacOptions ++= Seq(
      "-deprecation",         // warn on deprecated APIs
      "-feature",             // warn on feature imports/usages
      "-unchecked",           // extra checks for pattern matches, etc.
      "-Wvalue-discard",      // warn when a non-Unit value is ignored
      "-Wnonunit-statement",  // warn on statements that return non-Unit
      "-Wunused:imports"      // needed by Scalafix to use OrganizeImports.removeUnused
    ),

    // MUnit test framework
    testFrameworks += new TestFramework("munit.Framework"),

    // Generate a Scala object with the app version from SBT `version`
    Compile / sourceGenerators += Def.task {
      val out = (Compile / sourceManaged).value / "io" / "github" / "scala_tessella" / "editor" / "buildinfo"
      val file = out / "BuildInfo.scala"
      IO.write(
        file,
        s"""package io.github.scala_tessella.editor.buildinfo
           |object BuildInfo {
           |  val version: String = "${version.value}"
           |}
           |""".stripMargin
      )
      Seq(file)
    }.taskValue
  )
