import org.scalajs.linker.interface.ModuleSplitStyle
import org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv

// Enable semanticdb for Scalafix (Scala 3)
ThisBuild / semanticdbEnabled := true
ThisBuild / semanticdbVersion := scalafixSemanticdb.revision

// Optional: format when compiling (can be noisy in PRs; turn off if you prefer manual runs)
ThisBuild / scalafmtOnCompile := true

// Package layering enforcement. Fails the build if a layer imports from a
// layer it must not depend on. Allowed direction is one-way:
//   components/interactions → AppState → operations → models → utils.
lazy val checkLayering = taskKey[Unit]("Enforce one-way package layering")

// Keep the Scala menu shortcut table in sync with the Tauri menu's Rust
// constants. Fails the build if MenuShortcuts.scala and menu_shortcuts.rs
// drift. Soft-skips when the Rust file is absent (fresh checkout without
// the desktop shell scaffolded).
lazy val checkMenuShortcutsParity = taskKey[Unit]("Enforce menu-shortcut parity between Scala and Rust")

lazy val editor = project.in(file("."))
  .enablePlugins(ScalaJSPlugin) // Enable the Scala.js plugin in this project
  .settings(
    scalaVersion := "3.8.4",
    version := "0.6.2",
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
    libraryDependencies += "io.github.scala-tessella" %%% "ring-seq" % "0.8.0",
    libraryDependencies += "io.github.scala-tessella" %%% "dcel" % "0.1.3",

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

    // Package layering check — runs before every compile. Allowed edges:
    //   components/interactions → operations, models, utils, AppState
    //   AppState                → operations, models, utils
    //   operations              → models, utils
    //   models                  → utils
    //   utils                   → (nothing in this project)
    checkLayering := {
      val scalaDir =
        (Compile / sourceDirectory).value / "scala" / "art" / "tessell" / "editor"
      val rules: Seq[(String, Seq[String])] = Seq(
        "models"     -> Seq("AppState", "operations", "components", "interactions"),
        "operations" -> Seq("AppState", "components", "interactions"),
        "utils"      -> Seq("AppState", "components", "interactions")
      )
      val violations = rules.flatMap { case (layer, forbidden) =>
        val layerDir = scalaDir / layer
        val pattern  =
          ("""^\s*import\s+art\.tessell\.editor\.(""" +
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
          "Package layering violations — allowed direction is one-way " +
            "(components/interactions → AppState → operations → models → utils):\n" +
            violations.mkString("\n")
        )
      streams.value.log.info(s"Package layering check passed (${rules.size} layers)")
    },
    (Compile / compile) := (Compile / compile).dependsOn(checkLayering, checkMenuShortcutsParity).value,

    checkMenuShortcutsParity := {
      // Lift task-value lookups out of any conditional — sbt's linter rejects
      // `streams.value` / `baseDirectory.value` inside `if` branches because
      // `.value` is evaluated unconditionally regardless.
      val log       = streams.value.log
      val base      = baseDirectory.value
      val scalaFile =
        (Compile / sourceDirectory).value / "scala" / "art" / "tessell" / "editor" /
          "models" / "MenuShortcuts.scala"
      val rustFile  = base / "desktop" / "src-tauri" / "src" / "menu_shortcuts.rs"
      if (!rustFile.exists())
        log.warn(
          s"Menu-shortcut parity check skipped: ${base.toPath.relativize(rustFile.toPath)} not found"
        )
      else {
        val scalaSource = IO.read(scalaFile)
        val rustSource  = IO.read(rustFile)
        // The bindings map literal `MenuAction.FileSave -> ...` is the discovery surface;
        // reading the enum directly means tolerating multiline `case A, B, C` which is brittle.
        val actions     = """MenuAction\.(\w+)""".r
          .findAllMatchIn(scalaSource)
          .map(_.group(1))
          .toSet
        val rustNames   = """pub const ([A-Z][A-Z0-9_]*):""".r
          .findAllMatchIn(rustSource)
          .map(_.group(1))
          .toSet
        // PascalCase → SCREAMING_SNAKE_CASE, StringBuilder to stay 2.12-safe
        // (sbt's build-definition Scala) and avoid regex lookbehind.
        def screaming(s: String): String = {
          val sb = new StringBuilder
          s.foreach { c =>
            if (c.isUpper && sb.nonEmpty) sb.append('_')
            sb.append(c.toUpper)
          }
          sb.toString
        }
        val expected    = actions.map(screaming)
        val missing     = expected -- rustNames
        val orphan      = rustNames -- expected
        if (missing.nonEmpty || orphan.nonEmpty) {
          val missingLine =
            if (missing.nonEmpty)
              Some(s"  missing in menu_shortcuts.rs: ${missing.toSeq.sorted.mkString(", ")}")
            else None
          val orphanLine  =
            if (orphan.nonEmpty)
              Some(s"  orphan in menu_shortcuts.rs (no matching MenuAction): ${orphan.toSeq.sorted.mkString(", ")}")
            else None
          sys.error(
            "Menu-shortcut parity violations:\n" + Seq(missingLine, orphanLine).flatten.mkString("\n")
          )
        } else
          log.info(s"Menu-shortcut parity check passed (${actions.size} actions)")
      }
    },

    // Generate a Scala object with the app version from SBT `version`
    Compile / sourceGenerators += Def.task {
      val out = (Compile / sourceManaged).value / "art" / "tessell" / "editor" / "buildinfo"
      val file = out / "BuildInfo.scala"
      IO.write(
        file,
        s"""package art.tessell.editor.buildinfo
           |object BuildInfo {
           |  val version: String = "${version.value}"
           |}
           |""".stripMargin
      )
      Seq(file)
    }.taskValue
  )
