package io.github.scala_tessella.editor.interactions

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom.{MouseEvent, WheelEvent}
import io.github.scala_tessella.editor.models.{AppState, EditorState, Point}
import scala.math.{max, min}

object MouseHandler:
  def mouseEventHandlers: List[Modifier[?]] = List(
    onMouseDown --> handleMouseDown,
    onMouseMove --> handleMouseMove,
    onMouseUp --> handleMouseUp,
    onWheel --> handleWheel
  )

  def handleMouseDown(event: MouseEvent): Unit =
    event.preventDefault()
    EditorState.isDragging.set(true)
    EditorState.dragStart.set(Some(Point(event.clientX, event.clientY)))

  def handleMouseMove(event: MouseEvent): Unit =
    if EditorState.isDragging.now() then
      EditorState.dragStart.now().foreach { start =>
        val deltaX = event.clientX - start.x
        val deltaY = event.clientY - start.y
        EditorState.viewTransform.update(t => t.copy(
          panX = t.panX + deltaX,
          panY = t.panY + deltaY
        ))
        EditorState.dragStart.set(Some(Point(event.clientX, event.clientY)))
      }

  def handleMouseUp(event: MouseEvent): Unit =
    EditorState.isDragging.set(false)
    EditorState.dragStart.set(None)

  private def getCanvasRelativePosition(event: WheelEvent): Option[Point] =
    EditorState.canvasElementRef.now().map { canvasElement =>
      val rect = canvasElement.getBoundingClientRect()
      Point(
        event.clientX - rect.left,
        event.clientY - rect.top
      )
    }

  def handleWheel(event: WheelEvent): Unit =
    event.preventDefault()
    
    getCanvasRelativePosition(event).foreach { mousePos =>
      val currentTransform = EditorState.viewTransform.now()
      val scaleFactor = if (event.deltaY < 0) 1.1 else 0.9
      val newScale = max(0.1, min(5.0, currentTransform.scale * scaleFactor))
      
      // Calculate the world position that the mouse is pointing to before zoom
      val worldX = (mousePos.x - currentTransform.panX) / currentTransform.scale
      val worldY = (mousePos.y - currentTransform.panY) / currentTransform.scale
      
      // Calculate new pan to keep the world position under the mouse cursor
      val newPanX = mousePos.x - worldX * newScale
      val newPanY = mousePos.y - worldY * newScale

      EditorState.viewTransform.set(currentTransform.copy(
        scale = newScale,
        panX = newPanX,
        panY = newPanY
      ))
    }