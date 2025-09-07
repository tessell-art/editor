import org.scalajs.linker.interface.ModuleSplitStyle

lazy val editor = project.in(file("."))
  .enablePlugins(ScalaJSPlugin) // Enable the Scala.js plugin in this project
  .settings(
    scalaVersion := "3.7.2",
    version := "0.2.2",
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
    libraryDependencies += "org.typelevel" %%% "spire" % "0.18.0",
    libraryDependencies += "io.github.scala-tessella" %%% "ring-seq" % "0.6.2",
    libraryDependencies += "io.github.scala-tessella" %%% "tessella" % "0.3.0+108-3c7c12c6+20250713-1610",
    libraryDependencies += "io.github.scala-tessella" %%% "dcel" % "0.1.0-SNAPSHOT",

    // Test dependencies
    libraryDependencies += "org.scalameta" %%% "munit" % "1.0.0" % Test,

    // MUnit test framework
    testFrameworks += new TestFramework("munit.Framework")
  )
