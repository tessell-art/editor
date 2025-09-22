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
import scala.scalajs.js.timers.{SetTimeoutHandle, setTimeout, clearTimeout} // debouncing timers

object KeyboardEventHandler:

  def keyboardEventHandlers: Binder[HtmlElement] =
    // Gate the stream using the processing signal without calling .now() per keydown
    windowEvents(_.onKeyDown)
      .withCurrentValueOf(EditorState.isProcessing.signal)
      .collect { case (event, false) => event }
      --> handleKeyDown

  // --- Debounced rotate / zoom state ---
  private var rotateAccum: Int = 0
  private var rotateTimer: Option[SetTimeoutHandle] = None

  private var zoomAccum: Double = 1.0
  private var zoomTimer: Option[SetTimeoutHandle] = None

  private val debounceMs = 20 // ~1 frame @ 50fps; adjust 16–33ms as desired

  private def flushRotate(): Unit =
    if rotateAccum != 0 then
      ViewOperations.rotateView(rotateAccum)
      rotateAccum = 0

  private def flushZoom(): Unit =
    if zoomAccum != 1.0 then
      // Apply once, respecting existing scale limits
      val factor = zoomAccum
      zoomAccum = 1.0
      EditorState.viewTransform.update { t =>
        val next = t.scale * factor
        t.copy(scale = min(max(next, 0.1), 5.0))
      }

  private def enqueueRotate(delta: Int): Unit =
    rotateAccum += delta
    rotateTimer.foreach(clearTimeout)
    rotateTimer = Some(setTimeout(debounceMs) {
      flushRotate()
      rotateTimer = None
    })

  private def enqueueZoom(factor: Double): Unit =
    zoomAccum *= factor
    zoomTimer.foreach(clearTimeout)
    zoomTimer = Some(setTimeout(debounceMs) {
      flushZoom()
      zoomTimer = None
    })

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
      event.key match
        case "r" | "R" =>
          event.preventDefault()
          enqueueRotate(+15) // debounced
        case "e" | "E" =>
          event.preventDefault()
          enqueueRotate(-15) // debounced
        case "Z" if event.ctrlKey && event.shiftKey =>
          event.preventDefault()
          AppState.redoObserver
        case "z" if event.ctrlKey =>
          event.preventDefault()
          AppState.undoObserver
        case "s" if event.ctrlKey =>
          event.preventDefault()
          // Use the snapshots captured above
          if hasFileName && !currentTiling.isEmpty then
            SvgExporter.saveTilingToSVG()
        case "+" | "=" =>
          event.preventDefault()
          enqueueZoom(1.1) // debounced
        case "-" | "_" =>
          event.preventDefault()
          enqueueZoom(1.0 / 1.1) // debounced
        case "Escape" =>
          event.preventDefault()
          clearAllSelections()
        case "Delete" | "Backspace" =>
          event.preventDefault()
          // Future deletion logic can be added here
          ()
        case _ => ()