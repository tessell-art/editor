package io.github.scala_tessella.editor

import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.Logger
import io.github.scala_tessella.editor.utils.ColorRGB.*
import org.scalajs.dom

import scala.scalajs.js

/** Test-hook API exposed to end-to-end tests under `e2e/` (ADR-004).
  *
  * Registers a narrow, read-only object at `window.__tessellaTestHooks__` so Playwright smoke tests can make
  * domain-native assertions ("the tiling has 1 inner face") without screen-scraping SVG selectors that would
  * break on every CSS rename.
  *
  * Hooks are deliberately **observations, not mutations** — every state change an e2e test wants to trigger
  * should still go through a user-visible path (clicking a palette button, pressing a keyboard shortcut).
  * This keeps the hook object from growing into a back-door editor API. See ADR-004 §Consequences for the
  * rationale.
  *
  * The registered global lands in production bundles too. Payload is a handful of bytes per exported method
  * plus the object registration; if bundle size ever matters, gate the [[install]] call on a build-time flag
  * (ADR-004 §Consequences lists the options). Not done here on purpose — premature.
  *
  * Why manual installation rather than `@JSExportTopLevel`: this build uses `ModuleKind.ESModule` (see
  * `build.sbt`), under which `@JSExportTopLevel("name")` produces a *named ES module export* of the
  * containing module — not a `window.name` global. Playwright's `page.evaluate(() => window.X)` reads window
  * globals, so we install via `dom.window` directly. The [[install]] entry point is invoked from `Editor()`
  * on app startup so the hooks are reachable as soon as the page is interactive.
  *
  * The body is wrapped in a try/catch + warn-log so an unexpected install failure can never crash the
  * application (the editor must render even if hooks don't): failing here would only affect e2e tests, which
  * is the right blast radius.
  */
object TestHooks:

  /** Number of inner faces in the current tiling. Zero when the tiling is empty. */
  def tilingPolygonCount: Int =
    EditorState.tessellationState.now().currentTiling.innerFaces.size

  /** True when no tiling is loaded. Equivalent to `tilingPolygonCount == 0` on current state but cheaper and
    * named after the natural domain question.
    */
  def isTilingEmpty: Boolean =
    EditorState.tessellationState.now().currentTiling.isEmpty

  /** Current palette fill colour as a CSS `rgb(r,g,b)` string. */
  def currentFillColor: String =
    EditorState.colorState.now().fillColor.toRgb

  /** Install the hook object at `window.__tessellaTestHooks__`. Idempotent — overwrites any previous
    * registration, which doesn't matter in practice (only one app instance per page).
    *
    * Each hook is wrapped in an explicit `js.Function0[T]` `val` so the call site
    * `window.__tessellaTestHooks__.tilingPolygonCount()` works as a JS function call and re-reads
    * `EditorState` on every invocation. Passing the bare `def` would freeze the value at registration time.
    *
    * Uses explicit `updateDynamic("name")(value)` rather than the assignment-style `obj.name = value` syntax
    * — both compile to the same thing under `Dynamic`, but the explicit form is unambiguous about which
    * dynamic operation is happening at each step.
    */
  def install(): Unit =
    try
      val tilingPolygonCountFn: js.Function0[Int]  = () => tilingPolygonCount
      val isTilingEmptyFn: js.Function0[Boolean]   = () => isTilingEmpty
      val currentFillColorFn: js.Function0[String] = () => currentFillColor

      val obj = js.Dynamic.literal()
      obj.updateDynamic("tilingPolygonCount")(tilingPolygonCountFn)
      obj.updateDynamic("isTilingEmpty")(isTilingEmptyFn)
      obj.updateDynamic("currentFillColor")(currentFillColorFn)

      dom.window.asInstanceOf[js.Dynamic].updateDynamic("__tessellaTestHooks__")(obj)
      Logger.debug("TestHooks installed at window.__tessellaTestHooks__")
    catch
      case e: Throwable =>
        // Never let a hook-install failure block the app from rendering — that's the whole reason for
        // the try/catch. If you see this in dev, the e2e suite will fail; figure out why install threw,
        // don't strip the warning.
        Logger.warn(s"TestHooks.install failed: ${e.getMessage}")
