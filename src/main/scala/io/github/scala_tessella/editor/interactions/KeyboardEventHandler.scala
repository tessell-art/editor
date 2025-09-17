package io.github.scala_tessella.editor.interactions

import io.github.scala_tessella.editor.components.{EditorCanvasComponent, MenuBarComponent}
import io.github.scala_tessella.editor.models.{AppState, EditorMode, EditorState}
import io.github.scala_tessella.editor.operations.SelectionOperations.clearAllSelections
import io.github.scala_tessella.editor.operations.ViewOperations
import io.github.scala_tessella.editor.utils.SvgExporter

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import org.scalajs.dom.KeyboardEvent

import scala.math.{max, min}

object KeyboardEventHandler:

  def keyboardEventHandlers: Binder[HtmlElement] =
    // Gate the stream using the processing signal without calling .now() per keydown
    windowEvents(_.onKeyDown)
      .withCurrentValueOf(EditorState.isProcessing.signal)
      .collect { case (event, false) => event }
      --> handleKeyDown

  def handleKeyDown(event: KeyboardEvent): Unit =
    // Snapshot small pieces of state once per key event
    val currentTiling = EditorState.currentTiling.now()
    val hasFileName = EditorState.currentFileName.now().isDefined

    val targetIsInput = Option(event.target).exists {
      case _: dom.html.Input => true
      case _: dom.html.TextArea => true
      case _: dom.html.Select => true
      case el: dom.html.Element if el.isContentEditable => true
      case _ => false
    }

    if !targetIsInput then
      // Normalize key handling and support Mac (Meta) as accelerator
      val key = event.key
      val keyLower = key.toLowerCase
      val isAccel = event.ctrlKey || event.metaKey
      val isShift = event.shiftKey

      (keyLower, isAccel, isShift) match
        case ("r", false, _) =>
          event.preventDefault()
          ViewOperations.rotateView(+15)
        case ("e", false, _) =>
          event.preventDefault()
          ViewOperations.rotateView(-15)
        case ("z", true, true) =>
          // Ctrl+Shift+Z / Cmd+Shift+Z → Redo
          event.preventDefault()
          AppState.redo()
        case ("z", true, false) =>
          // Ctrl+Z / Cmd+Z → Undo
          event.preventDefault()
          AppState.undo()
        case ("s", true, true) =>
          // Ctrl+Shift+S / Cmd+Shift+S → Save As (suppress browser Save As even if app doesn't handle it)
          event.preventDefault()
          ()
        case ("s", true, false) =>
          // Ctrl+S / Cmd+S → Save
          event.preventDefault() // always prevent to avoid browser save dialog
          if hasFileName && !currentTiling.isEmpty then
            SvgExporter.saveTilingToSVG()
        case ("+", _, _) | ("=", _, _) =>
          event.preventDefault()
          EditorState.viewTransform.update(t => t.copy(scale = min(t.scale * 1.1, 5.0)))
        case ("-", _, _) | ("_", _, _) =>
          event.preventDefault()
          EditorState.viewTransform.update(t => t.copy(scale = max(t.scale / 1.1, 0.1)))
        case ("escape", _, _) =>
          event.preventDefault()
          clearAllSelections()
        case ("delete", _, _) | ("backspace", _, _) =>
          event.preventDefault()
          // Future deletion logic can be added here
          ()
        case _ =>
          ()
