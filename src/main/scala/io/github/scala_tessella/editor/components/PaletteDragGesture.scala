package io.github.scala_tessella.editor.components

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.operations.PaletteDragOperations
import io.github.scala_tessella.editor.utils.geo.Point
import org.scalajs.dom

/** Pointer-events drag-from-palette gesture (Phase 5.6). Attaches to any palette button: regular, custom-n,
  * rhombus, MRU slot. The gesture fires on top of the existing click handlers — a tap still selects +
  * retracts the sheet exactly as before, while crossing the drag threshold takes over the dotted-edge preview
  * and commits a placement on release.
  *
  * Why pointer events (and not parallel mouse + touch handlers): the palette → canvas drag is one conceptual
  * gesture. Sharing one event family lets `setPointerCapture` keep events flowing to the source button even
  * after the cursor has crossed onto the canvas. The canvas's own pan/zoom paths stay on
  * `MouseEventHandler`/`TouchEventHandler` because they have genuinely platform-specific semantics (pinch +
  * two-finger rotate on touch; wheel on mouse).
  */
object PaletteDragGesture:

  /** Drag-shape closure handed in by each button kind. `angles` is the angle vector to preview;
    * `selectInPalette` runs the same selection-state mutations the click path performs (so the placement op
    * reads the right shape from `EditorState` when the gesture commits).
    */
  case class DragShape(
      angles: Vector[AngleDegree],
      selectInPalette: () => Unit
  )

  // 5 logical px squared — distance below which we treat the gesture as a tap and let the existing
  // click handler run untouched.
  private val DRAG_THRESHOLD_SQUARED: Double = 25.0

  // Component-local in-flight state. A single drag at a time (one pointer captured by one button),
  // so plain Vars are enough — no per-button instances.
  private val dragOrigin: Var[Option[Point]]       = Var(None)
  private val dragShapeRef: Var[Option[DragShape]] = Var(None)
  private val dragStarted: Var[Boolean]            = Var(false)

  /** Modifier suite. Pass the closure that produces the shape + selection at pointerdown time — this lets
    * custom-n / rhombus inputs read their *current* values when the gesture starts, rather than capturing
    * them at element construction.
    */
  def modifiers(snapshotShape: () => DragShape): Seq[Modifier[Element]] =
    // Note: do NOT call preventDefault on pointerdown — that would suppress the synthetic click
    // that fires on a tap-without-drag, which the existing onClick handler still owns.
    // `touch-action: none` on the button (CSS) is what stops the browser's pan/zoom from stealing
    // mid-drag pointermoves on touch.
    Seq(
      onPointerDown --> { event =>

        startDrag(event, snapshotShape())
      },
      onPointerMove --> { event =>

        updateDrag(event)
      },
      onPointerUp --> { event =>

        endDrag(event, commit = true)
      },
      onPointerCancel --> { event =>

        endDrag(event, commit = false)
      }
    )

  private def startDrag(event: dom.PointerEvent, shape: DragShape): Unit =
    if EditorState.uiState.now().isProcessing then ()
    else
      dragOrigin.set(Some(Point(event.clientX, event.clientY)))
      dragShapeRef.set(Some(shape))
      dragStarted.set(false)
      // Capture so subsequent move/up events stay with the button even when the cursor crosses onto
      // the canvas. Best-effort: not all environments (older browsers, JSDOM) implement it.
      try event.currentTarget.asInstanceOf[dom.HTMLElement].setPointerCapture(event.pointerId)
      catch case _: Throwable => ()

  private def updateDrag(event: dom.PointerEvent): Unit =
    (dragOrigin.now(), dragShapeRef.now()) match
      case (Some(origin), Some(shape)) =>
        val current = Point(event.clientX, event.clientY)
        val delta   = current - origin
        val started = dragStarted.now()
        if !started then
          if delta.dot(delta) > DRAG_THRESHOLD_SQUARED then
            dragStarted.set(true)
            shape.selectInPalette()
            EditorState.uiState.update(_.copy(isPaletteDragActive = true))
            applySnap(event.clientX, event.clientY, shape.angles)
        else
          applySnap(event.clientX, event.clientY, shape.angles)
      case _                           => ()

  private def applySnap(clientX: Double, clientY: Double, angles: Vector[AngleDegree]): Unit =
    PaletteDragOperations.applyDragStep(clientX, clientY, angles)

  private def endDrag(event: dom.PointerEvent, commit: Boolean): Unit =
    val wasDragging = dragStarted.now()
    if wasDragging then
      if commit then PaletteDragOperations.commitDragRelease()
      else PaletteDragOperations.cancelDrag()
    resetLocal()

  private def resetLocal(): Unit =
    dragOrigin.set(None)
    dragShapeRef.set(None)
    dragStarted.set(false)
