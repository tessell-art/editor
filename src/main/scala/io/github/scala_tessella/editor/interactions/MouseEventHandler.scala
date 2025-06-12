package io.github.scala_tessella.editor.interactions

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom.{MouseEvent, WheelEvent}
import io.github.scala_tessella.editor.models.{AppState, Point}
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
    AppState.isDragging.set(true)
    AppState.dragStart.set(Some(Point(event.clientX, event.clientY)))

  def handleMouseMove(event: MouseEvent): Unit =
    if (AppState.isDragging.now()) {
      AppState.dragStart.now().foreach { start =>
        val deltaX = event.clientX - start.x
        val deltaY = event.clientY - start.y
        AppState.viewTransform.update(t => t.copy(
          panX = t.panX + deltaX,
          panY = t.panY + deltaY
        ))
        AppState.dragStart.set(Some(Point(event.clientX, event.clientY)))
      }
    }

  def handleMouseUp(event: MouseEvent): Unit =
    AppState.isDragging.set(false)
    AppState.dragStart.set(None)

  private def getCanvasRelativePosition(event: WheelEvent): Option[Point] =
    AppState.canvasElementRef.now().map { canvasElement =>
      val rect = canvasElement.getBoundingClientRect()
      Point(
        event.clientX - rect.left,
        event.clientY - rect.top
      )
    }

  def handleWheel(event: WheelEvent): Unit =
    event.preventDefault()
    
    getCanvasRelativePosition(event).foreach { mousePos =>
      val currentTransform = AppState.viewTransform.now()
      val scaleFactor = if (event.deltaY < 0) 1.1 else 0.9
      val newScale = max(0.1, min(5.0, currentTransform.scale * scaleFactor))
      
      // Calculate the world position that the mouse is pointing to before zoom
      val worldX = (mousePos.x - currentTransform.panX) / currentTransform.scale
      val worldY = (mousePos.y - currentTransform.panY) / currentTransform.scale
      
      // Calculate new pan to keep the world position under the mouse cursor
      val newPanX = mousePos.x - worldX * newScale
      val newPanY = mousePos.y - worldY * newScale
      
      AppState.viewTransform.set(currentTransform.copy(
        scale = newScale,
        panX = newPanX,
        panY = newPanY
      ))
    }