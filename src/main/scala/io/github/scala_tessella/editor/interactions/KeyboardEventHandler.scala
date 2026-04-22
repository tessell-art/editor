package io.github.scala_tessella.editor.interactions

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.models.{EditorConfig, EditorState}
import io.github.scala_tessella.editor.operations.SelectionOperations.clearAllSelections
import io.github.scala_tessella.editor.operations.ViewOperations
import io.github.scala_tessella.editor.utils.file.SvgExporter
import io.github.scala_tessella.editor.utils.UndoManager
import org.scalajs.dom
import org.scalajs.dom.KeyboardEvent

object KeyboardEventHandler:

  private inline def isMac: Boolean =
    // Uses platform to detect macOS reliably across Chrome/Safari/Firefox
    dom.window.navigator.platform.toLowerCase.contains("mac")

  private inline def primaryMod(e: KeyboardEvent): Boolean =
    // Command on macOS, Ctrl elsewhere
    if isMac then e.metaKey else e.ctrlKey

  def keyboardEventHandlers: Mod[HtmlElement] =
    onMountCallback: ctx =>

      val owner    = ctx.owner
      // Gate the stream using the processing signal without calling .now() per keydown
      val keyDowns = windowEvents(_.onKeyDown)
        .withCurrentValueOf(EditorState.isProcessing.signal)
        .collect:
          case (event, false) => event

      val rotationStream = keyDowns
        .map: event =>
          if isTargetInput(event) then None
          else
            rotationDeltaForKey(event.key).map: delta =>

              event.preventDefault()
              delta
        .collect:
          case Some(delta) => delta

      val zoomStream = keyDowns
        .map: event =>
          if isTargetInput(event) then None
          else
            zoomFactorForKey(event.key).map: factor =>

              event.preventDefault()
              factor
        .collect:
          case Some(factor) => factor

      rotationStream
        .debounce(debounceMs)
        .foreach(ViewOperations.rotateView)(using owner): Unit

      zoomStream
        .debounce(debounceMs)
        .foreach { factor =>

          EditorState.viewState.update: s =>

            val vt   = s.viewTransform
            val next = vt.scale * factor
            s.copy(viewTransform = vt.copy(scale = ViewOperations.clampViewScale(next)))
        }(using owner): Unit

      keyDowns
        .filter: event =>
          !isTargetInput(event) &&
            rotationDeltaForKey(event.key).isEmpty &&
            zoomFactorForKey(event.key).isEmpty
        .withCurrentValueOf(
          EditorState.tessellationState.signal.map(_.currentTiling).distinct,
          EditorState.currentFileName.signal
        )
        .foreach { (event, tiling, fileNameOpt) =>

          handleKeyDown(event, tiling, fileNameOpt.isDefined)
        }(using owner): Unit

  // --- Debounced rotate / zoom config ---
  private val debounceMs = 20 // ~1 frame @ 50fps; adjust 16–33ms as desired

  private[interactions] def rotationDeltaForKey(key: String): Option[Int] =
    key match
      case "r" | "R" => Some(15)
      case "e" | "E" => Some(-15)
      case _         => None

  private[interactions] def zoomFactorForKey(key: String): Option[Double] =
    key match
      case "+" | "=" => Some(EditorConfig.keyboardZoomFactor)
      case "-" | "_" => Some(1.0 / EditorConfig.keyboardZoomFactor)
      case _         => None

  private[interactions] def isUndoShortcut(key: String, primary: Boolean, shift: Boolean): Boolean =
    primary && key == "z" && !shift

  private[interactions] def isRedoShortcut(key: String, primary: Boolean, shift: Boolean): Boolean =
    primary && shift && (key == "Z" || key == "z")

  private[interactions] def isSaveShortcut(key: String, primary: Boolean): Boolean =
    primary && key == "s"

  private def isTargetInput(event: KeyboardEvent): Boolean =
    Option(event.target).exists {
      case _: dom.html.Input                            => true
      case _: dom.html.TextArea                         => true
      case _: dom.html.Select                           => true
      case el: dom.html.Element if el.isContentEditable => true
      case _                                            => false
    }

  def handleKeyDown(event: KeyboardEvent, currentTiling: TilingDCEL, hasFileName: Boolean): Unit =
    if !isTargetInput(event) then
      if (event.key == "d" || event.key == "D") && !currentTiling.isEmpty then
        event.preventDefault()
        AppState.doubleTiling()
      else if event.key == "f" || event.key == "F" then
        event.preventDefault()
        AppState.fitTilingToCanvas()
      else if isRedoShortcut(event.key, primaryMod(event), event.shiftKey) then
        event.preventDefault()
        UndoManager.redo()
      else if isUndoShortcut(event.key, primaryMod(event), event.shiftKey) then
        event.preventDefault()
        UndoManager.undo()
      else if isSaveShortcut(event.key, primaryMod(event)) then
        event.preventDefault()
        // Use the snapshots captured above
        if hasFileName && !currentTiling.isEmpty then
          SvgExporter.saveTilingToSVG()
      else if event.key == "Escape" then
        event.preventDefault()
        clearAllSelections()
      else if event.key == "Delete" || event.key == "Backspace" then
        event.preventDefault()
        // Future deletion logic can be added here
        ()
      else ()
