package io.github.scala_tessella.editor.interactions

import io.github.scala_tessella.editor.models.EditorState

import com.raquo.laminar.api.L.{*, given}
import org.scalajs.dom
import org.scalajs.dom.TouchEvent

object TouchEventHandler {

  // For pinch-zoom and rotate
  private val initialTouchDistance = Var[Option[Double]](None)
  private val initialScale = Var[Option[Double]](None)
  private val initialAngle = Var[Option[Double]](None)
  private val initialRotation = Var[Option[Double]](None)

  // For single-touch pan
  private val lastPanPoint = Var[Option[(Double, Double)]](None)

  // To distinguish tap from drag
  private val touchStartPoint = Var[Option[(Double, Double)]](None)
  private val isDragging = Var[Boolean](false)
  private val DRAG_THRESHOLD_SQUARED = 25.0 // Using squared distance to avoid sqrt

  def handleTouchStart(event: TouchEvent): Unit = {
    val touches = event.touches
    if (touches.length == 1) {
      val touch = touches(0)
      // This could be a tap, so we don't prevent default actions yet.
      touchStartPoint.set(Some((touch.clientX, touch.clientY)))
      lastPanPoint.set(Some((touch.clientX, touch.clientY)))
      isDragging.set(false)
    } else if (touches.length == 2) {
      // A two-finger gesture is for zooming and rotating, not tapping.
      event.preventDefault()
      isDragging.set(true) // Mark as dragging to suppress tap behavior
      val touch1 = event.touches(0)
      val touch2 = event.touches(1)

      val distance = getDistance(touch1, touch2)
      initialTouchDistance.set(Some(distance))
      initialScale.set(Some(EditorState.viewTransform.now().scale))

      val angle = getAngle(touch1, touch2)
      initialAngle.set(Some(angle))
      initialRotation.set(Some(EditorState.viewTransform.now().rotationDegrees))
    } else {
      // More than two fingers, we'll just prevent default browser behavior.
      isDragging.set(true)
      event.preventDefault()
    }
  }

  def handleTouchMove(event: TouchEvent): Unit = {
    val touches = event.touches

    if (touches.length == 1) {
      touchStartPoint.now().foreach { startPoint =>
        val touch = touches(0)
        if (!isDragging.now()) {
          // Check if the movement has passed our threshold to be considered a drag.
          val dx = touch.clientX - startPoint._1
          val dy = touch.clientY - startPoint._2
          if (dx * dx + dy * dy > DRAG_THRESHOLD_SQUARED) {
            isDragging.set(true)
          }
        }

        if (isDragging.now()) {
          // Now that it's a confirmed drag, prevent default actions like page scrolling.
          event.preventDefault()
          lastPanPoint.now().foreach { lastPoint =>
            val panDx = touch.clientX - lastPoint._1
            val panDy = touch.clientY - lastPoint._2
            EditorState.viewTransform.update(t => t.copy(
              panX = t.panX + panDx,
              panY = t.panY + panDy
            ))
            lastPanPoint.set(Some((touch.clientX, touch.clientY)))
          }
        }
      }
    } else if (touches.length == 2) {
      (initialTouchDistance.now(), initialScale.now(), initialAngle.now(), initialRotation.now()) match {
        case (Some(initialDist), Some(initScale), Some(initAngle), Some(initRotation)) =>
          event.preventDefault()
          val touch1 = touches(0)
          val touch2 = touches(1)

          val newDist = getDistance(touch1, touch2)
          val newScale = if (initialDist > 0) initScale * (newDist / initialDist) else initScale

          val newAngle = getAngle(touch1, touch2)
          val rotationDelta = newAngle - initAngle
          val newRotation = initRotation + rotationDelta

          EditorState.viewTransform.update(_.copy(scale = newScale, rotationDegrees = newRotation))
        case _ => // State wasn't correctly initialized for zoom/rotate.
      }
    }
  }

  def handleTouchEnd(event: TouchEvent): Unit = {
    if (isDragging.now()) {
      // If a drag occurred, we prevent any potential 'ghost clicks' on some browsers.
      event.preventDefault()
    }
    // A tap is completed when touch ends without dragging.
    // We don't call preventDefault, allowing a 'click' event to be fired.
    resetTouchState()
  }

  def handleTouchCancel(event: TouchEvent): Unit = {
    // Treat cancel the same as end.
    resetTouchState()
  }

  private def resetTouchState(): Unit = {
    initialTouchDistance.set(None)
    initialScale.set(None)
    initialAngle.set(None)
    initialRotation.set(None)
    lastPanPoint.set(None)
    touchStartPoint.set(None)
    isDragging.set(false)
  }

  private def getDistance(touch1: dom.Touch, touch2: dom.Touch): Double = {
    val dx = touch1.clientX - touch2.clientX
    val dy = touch1.clientY - touch2.clientY
    Math.sqrt(dx * dx + dy * dy)
  }

  private def getAngle(touch1: dom.Touch, touch2: dom.Touch): Double = {
    val dx = touch2.clientX - touch1.clientX
    val dy = touch2.clientY - touch1.clientY
    Math.toDegrees(Math.atan2(dy, dx))
  }
}