package io.github.scala_tessella.editor.interactions

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.Geometry.*
import org.scalajs.dom.{MouseEvent, WheelEvent}

import scala.math.{max, min}

object MouseEventHandler:
  def mouseEventHandlers: List[Modifier[?]] = List(
    onMouseDown --> handleMouseDown,
    onMouseMove --> handleMouseMove,
    onMouseUp --> handleMouseUp,
    onWheel --> handleWheel
  )

  def handleMouseDown(event: MouseEvent): Unit =
    event.preventDefault()
    // Snapshot once
    EditorState.isDragging.set(true)
    val point: Point2 = Point2(event.clientX, event.clientY)
    EditorState.dragStart.set(Some(point))

  def handleMouseMove(event: MouseEvent): Unit =
    // Snapshot once per event
    val dragging     = EditorState.isDragging.now()
    val dragStartOpt = EditorState.dragStart.now()

    if dragging then
      dragStartOpt.foreach { start =>
        val deltaX        = event.clientX - start.xx
        val deltaY        = event.clientY - start.yy
        EditorState.viewTransform.update(t =>
          t.copy(
            panX = t.panX + deltaX,
            panY = t.panY + deltaY
          )
        )
        // Update the new "last" drag start once
        val point: Point2 = Point2(event.clientX, event.clientY)
        EditorState.dragStart.set(Some(point))
      }

  def handleMouseUp(event: MouseEvent): Unit =
    // Clear once
    EditorState.isDragging.set(false)
    EditorState.dragStart.set(None)

  private def getCanvasRelativePosition(event: WheelEvent): Option[Point2] =
    // Snapshot once
    EditorState.canvasElementRef.now().map { canvasElement =>
      val rect = canvasElement.getBoundingClientRect()
      Point2(
        event.clientX - rect.left,
        event.clientY - rect.top
      )
    }

  def handleWheel(event: WheelEvent): Unit =
    event.preventDefault()

    // Snapshot once per event
    val currentTransform = EditorState.viewTransform.now()

    getCanvasRelativePosition(event).foreach { (mousePos: Point2) =>
      val scaleFactor = if (event.deltaY < 0) 1.1 else 0.9
      val newScale    = max(0.1, min(5.0, currentTransform.scale * scaleFactor))

      // Calculate the world position that the mouse is pointing to before zoom
      val worldX = (mousePos.xx - currentTransform.panX) / currentTransform.scale
      val worldY = (mousePos.yy - currentTransform.panY) / currentTransform.scale

      // Calculate new pan to keep the world position under the mouse cursor
      val newPanX = mousePos.xx - worldX * newScale
      val newPanY = mousePos.yy - worldY * newScale

      EditorState.viewTransform.set(currentTransform.copy(
        scale = newScale,
        panX = newPanX,
        panY = newPanY
      ))
    }

  // Keep a name-compatible alias to match onWheel wiring used in the canvas
  def handleMouseWheel(event: WheelEvent): Unit =
    handleWheel(event)
