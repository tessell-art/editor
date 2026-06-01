package io.github.scala_tessella.editor.platform.desktop

import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.operations.{UndoManager, ViewOperations}
import io.github.scala_tessella.editor.utils.Logger
import io.github.scala_tessella.editor.utils.file.{DotExporter, SvgExporter}
import org.scalajs.dom

import scala.scalajs.js

/** Subscribes to the Tauri native menu's "menu" event and dispatches each item id to the same AppState /
  * EditorState entry point the DOM menu in [[io.github.scala_tessella.editor.components.MenuBarComponent]]
  * already uses.
  *
  * Dormant on the web — [[isTauri]] guards the install so a web-served build is unaffected. Bundle cost in
  * the web output is a handful of bytes (the match cascade), not worth gating on a build-time flag.
  *
  * The JS side is reached via `window.__TAURI__`, which is exposed because `app.withGlobalTauri` is `true` in
  * `tauri.conf.json`. That keeps the Scala.js code free of `@tauri-apps/api` imports — no new npm dependency,
  * no extra entry in `vite.config.js`.
  *
  * Menu item ids are authoritative: they're the strings Rust emits in `desktop/src-tauri/src/menu.rs`. Adding
  * a new menu entry is two edits (menu.rs + this dispatch).
  */
object DesktopMenuBridge:

  def install(): Unit =
    if isTauri then
      try
        val tauri                                   = dom.window.asInstanceOf[js.Dynamic].__TAURI__
        val handler: js.Function1[js.Dynamic, Unit] = (event: js.Dynamic) =>

          val id = event.payload.asInstanceOf[String]
          dispatch(id)
        // `val _ =` silences -Wnonunit-statement; the suggested `: Unit`
        // ascription fires E175 anyway in this spot (Scala 3 warning runs
        // before the ascription is resolved).
        val _ = tauri.event.listen("menu", handler)
        Logger.debug("DesktopMenuBridge installed (Tauri runtime detected)")
      catch
        case e: Throwable =>
          // Never let a bridge failure block the app; the DOM menu still works.
          Logger.warn(s"DesktopMenuBridge.install failed: ${e.getMessage}")

  private def isTauri: Boolean =
    val tauri = dom.window.asInstanceOf[js.Dynamic].selectDynamic("__TAURI__")
    !js.isUndefined(tauri) && tauri != null

  private def dispatch(id: String): Unit = id match
    case "new"                          => AppState.newTiling()
    case "load-svg"                     => AppState.loadSvgFile()
    case "save-svg"                     => SvgExporter.saveTilingToSVG()
    case "save-svg-as"                  => SvgExporter.saveAsTilingToSVG()
    case "export-dot"                   => DotExporter.exportTilingToDOT()
    case "settings"                     => EditorState.popupState.update(_.copy(showSettingsPopup = true))
    case "undo"                         => UndoManager.undo()
    case "redo"                         => UndoManager.redo()
    case "clear-tiling"                 => AppState.clearTiling()
    case "mirror"                       => AppState.mirrorTiling()
    case "select-all-polygons"          => AppState.selectAll()
    case "deselect-all"                 => AppState.deselectAll()
    case "fill-color"                   =>
      val current = EditorState.colorState.now().fillColor
      EditorState.colorState.update(_.copy(tempColor = current, showColorPicker = true))
    case "toggle-node-labels"           => AppState.toggleNodeLabels()
    case "toggle-uniformity"            => AppState.toggleShowUniformity()
    case "toggle-rotational-symmetry"   => AppState.toggleShowRotation()
    case "toggle-reflectional-symmetry" => AppState.toggleShowReflection()
    case "fit-to-canvas"                => AppState.fitTilingToCanvas()
    case "reset-view"                   => ViewOperations.resetView()
    case "zoom-in"                      => ViewOperations.zoomIn()
    case "zoom-out"                     => ViewOperations.zoomOut()
    case "rotate-left"                  => ViewOperations.rotateView(-30)
    case "rotate-right"                 => ViewOperations.rotateView(30)
    case "guide"                        => EditorState.popupState.update(_.copy(showGuidePopup = true))
    case "keyboard-shortcuts"           => EditorState.popupState.update(_.copy(showShortcutsPopup = true))
    case "about"                        => EditorState.popupState.update(_.copy(showAboutPopup = true))
    case other                          => Logger.warn(s"DesktopMenuBridge: unknown menu id '$other'")
