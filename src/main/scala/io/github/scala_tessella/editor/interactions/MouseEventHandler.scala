package io.github.scala_tessella.editor.interactions

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.models.{EditorConfig, EditorState}
import io.github.scala_tessella.editor.operations.ViewOperations
import io.github.scala_tessella.editor.utils.geo.Point
import org.scalajs.dom.{MouseEvent, WheelEvent}

object MouseEventHandler:
  private[interactions] def calculateZoomTransform(
      currentTransform: io.github.scala_tessella.editor.models.ViewTransform,
      mousePos: Point,
      scaleFactor: Double
  ): io.github.scala_tessella.editor.models.ViewTransform =
    val newScale = ViewOperations.clampViewScale(currentTransform.scale * scaleFactor)
    // Calculate the world position that the mouse is pointing to before zoom
    val world    = (mousePos - currentTransform.pan) / currentTransform.scale
    // Calculate new pan to keep the world position under the mouse cursor
    val newPan   = mousePos - world * newScale
    currentTransform.copy(
      scale = newScale,
      pan = newPan
    )

  def mouseEventHandlers: List[Modifier[?]] = List(
    onMouseDown --> handleMouseDown,
    onMouseMove --> handleMouseMove,
    onMouseUp --> handleMouseUp,
    onWheel --> handleWheel
  )

  def handleMouseDown(event: MouseEvent): Unit =
    event.preventDefault()
    // Snapshot once
    EditorState.uiState.update(_.copy(isDragging = true))
    val point: Point = Point(event.clientX, event.clientY)
    EditorState.uiState.update(_.copy(dragStart = Some(point)))

  def handleMouseMove(event: MouseEvent): Unit =
    // Snapshot once per event
    val dragging     = EditorState.uiState.now().isDragging
    val dragStartOpt = EditorState.uiState.now().dragStart

    if dragging then
      dragStartOpt.foreach { start =>

        val eventPoint   = Point(event.clientX, event.clientY)
        val delta: Point = eventPoint - start
        EditorState.viewState.update: s =>

          val vt = s.viewTransform
          s.copy(viewTransform = ViewOperations.clampedTransform(vt.copy(pan = vt.pan + delta)))
        // Update the new "last" drag start once
        val point: Point = Point(event.clientX, event.clientY)
        EditorState.uiState.update(_.copy(dragStart = Some(point)))
      }

  def handleMouseUp(event: MouseEvent): Unit =
    // Clear once
    EditorState.uiState.update(_.copy(isDragging = false))
    EditorState.uiState.update(_.copy(dragStart = None))

  private def getCanvasRelativePosition(event: WheelEvent): Option[Point] =
    // Snapshot once
    EditorState.uiState.now().canvasElementRef.map { canvasElement =>

      val rect = canvasElement.getBoundingClientRect()
      Point(
        event.clientX - rect.left,
        event.clientY - rect.top
      )
    }

  def handleWheel(event: WheelEvent): Unit =
    event.preventDefault()

    // Snapshot once per event
    val currentTransform = EditorState.viewState.now().viewTransform

    getCanvasRelativePosition(event).foreach { (mousePos: Point) =>

      val scaleFactor  =
        if event.deltaY < 0 then EditorConfig.mouseWheelZoomInFactor
        else EditorConfig.mouseWheelZoomOutFactor
      val newTransform = calculateZoomTransform(currentTransform, mousePos, scaleFactor)
      EditorState.viewState.update(_.copy(viewTransform = ViewOperations.clampedTransform(newTransform)))
    }

  // Keep a name-compatible alias to match onWheel wiring used in the canvas
  def handleMouseWheel(event: WheelEvent): Unit =
    handleWheel(event)
