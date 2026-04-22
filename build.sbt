import org.scalajs.linker.interface.ModuleSplitStyle
import org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv

// Enable semanticdb for Scalafix (Scala 3)
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

// Optional: format when compiling (can be noisy in PRs; turn off if you prefer manual runs)
ThisBuild / scalafmtOnCompile := true

// ADR-001 — Package layering enforcement
// Fails the build if a layer imports from a layer it must not depend on.
// See docs/adr/001-package-layering.md.
lazy val checkLayering = taskKey[Unit]("Enforce ADR-001 package layering")

lazy val editor = project.in(file("."))
  .enablePlugins(ScalaJSPlugin) // Enable the Scala.js plugin in this project
  .settings(
    scalaVersion := "3.8.3",
    version := "0.3.5",
    name := "Tessella Editor",

    // TODO: remove once dcel publishes 0.1.0-SNAPSHOT to a public repo.
    // Vendored Ivy-style artifacts live under lib-repo/.
    resolvers += Resolver.file("local-lib", file("lib-repo"))(Resolver.ivyStylePatterns),

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
    libraryDependencies += "io.github.scala-tessella" %%% "ring-seq" % "0.8.0",
    libraryDependencies += "io.github.scala-tessella" %%% "dcel" % "0.1.0-SNAPSHOT",

    // Test dependencies
    libraryDependencies += "org.scalameta" %%% "munit" % "1.0.0" % Test,
    libraryDependencies += "org.scalameta" %%% "munit-scalacheck" % "1.0.0" % Test,

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

    // Use JSDOM-backed Node.js environment for Scala.js tests that touch DOM APIs
    Test / jsEnv := new JSDOMNodeJSEnv(),
    // JSDOM env expects a script input (no module)
    Test / scalaJSLinkerConfig ~= (_.withModuleKind(ModuleKind.NoModule)),

    // ADR-001 layering check — runs before every compile.
    // Allowed edges:
    //   components/interactions → operations, models, utils, AppState
    //   AppState                → operations, models, utils
    //   operations              → models, utils
    //   models                  → utils
    //   utils                   → (nothing in this project)
    checkLayering := {
      val scalaDir =
        (Compile / sourceDirectory).value / "scala" / "io" / "github" / "scala_tessella" / "editor"
      val rules: Seq[(String, Seq[String])] = Seq(
        "models"     -> Seq("AppState", "operations", "components", "interactions"),
        "operations" -> Seq("AppState", "components", "interactions"),
        "utils"      -> Seq("AppState", "components", "interactions")
      )
      val violations = rules.flatMap { case (layer, forbidden) =>
        val layerDir = scalaDir / layer
        val pattern  =
          ("""^\s*import\s+io\.github\.scala_tessella\.editor\.(""" +
            forbidden.mkString("|") + """)($|[\s.{])""").r
        (layerDir ** "*.scala").get.flatMap { f =>
          IO.readLines(f).zipWithIndex.collect {
            case (line, i) if pattern.findFirstIn(line).isDefined =>
              s"  $layer/${f.getName}:${i + 1}: ${line.trim}"
          }
        }
      }
      if (violations.nonEmpty)
        sys.error(
          "ADR-001 layering violations (see docs/adr/001-package-layering.md):\n" +
            violations.mkString("\n")
        )
      streams.value.log.info(s"ADR-001 layering check passed (${rules.size} layers)")
    },
    (Compile / compile) := (Compile / compile).dependsOn(checkLayering).value,

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
