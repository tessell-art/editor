package io.github.scala_tessella.editor.interactions

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.models.{EditorConfig, EditorState, ViewTransform}
import io.github.scala_tessella.editor.utils.Geometry.{Point, Radian}
import org.scalajs.dom
import org.scalajs.dom.TouchEvent

object TouchEventHandler:

  // For pinch-zoom and rotate
  private val initialTouchDistance = Var[Option[Double]](None)
  private val initialScale         = Var[Option[Double]](None)
  private val initialAngle         = Var[Option[Double]](None)
  private val initialRotation      = Var[Option[Double]](None)
  private val pinchAnchorPoint     = Var[Option[Point]](None) // World coordinates

  // For single-touch pan
  private val lastPanPoint = Var[Option[Point]](None)

  // To distinguish tap from drag
  private val touchStartPoint        = Var[Option[Point]](None)
  private val isDragging             = Var[Boolean](false)
  private val DRAG_THRESHOLD_SQUARED = 25.0 // Using squared distance to avoid sqrt

  def handleTouchStart(event: TouchEvent): Unit =
    val touches = event.touches
    if touches.length == 1 then
      val touch = touches(0)
      val p     = Point(touch.clientX, touch.clientY)
      touchStartPoint.set(Some(p))
      lastPanPoint.set(Some(p))
      isDragging.set(false)
    else if touches.length == 2 then
      event.preventDefault()
      isDragging.set(true)
      val touch1 = touches(0)
      val touch2 = touches(1)

      initialTouchDistance.set(Some(getDistance(touch1, touch2)))
      initialAngle.set(Some(getAngle(touch1, touch2)))

      val currentTransform = EditorState.viewTransform.now()
      initialScale.set(Some(currentTransform.scale))
      initialRotation.set(Some(currentTransform.rotationDegrees))

      // Set anchor point for zooming
      EditorState.canvasElementRef.now().foreach { canvasElement =>
        val canvasRect     = canvasElement.getBoundingClientRect()
        val gestureCenterX = (touch1.clientX + touch2.clientX) / 2
        val gestureCenterY = (touch1.clientY + touch2.clientY) / 2
        val pointerX       =
          (gestureCenterX - canvasRect.left) * (EditorConfig.canvasViewBoxWidth / canvasRect.width)
        val pointerY       =
          (gestureCenterY - canvasRect.top) * (EditorConfig.canvasViewBoxHeight / canvasRect.height)

        val worldPoint = screenToWorld(Point(pointerX, pointerY), currentTransform)
        pinchAnchorPoint.set(Some(worldPoint))
      }
    else
      isDragging.set(true)
      event.preventDefault()

  def handleTouchMove(event: TouchEvent): Unit =
    val touches = event.touches

    if touches.length == 1 then
      val startPointOpt = touchStartPoint.now()
      val dragging      = isDragging.now()
      val lastPanOpt    = lastPanPoint.now()

      startPointOpt.foreach { startPoint =>
        val touch      = touches(0)
        val touchPoint = Point(touch.clientX, touch.clientY)
        if !dragging then
          val drag = touchPoint - startPoint
          if drag.dot(drag) > DRAG_THRESHOLD_SQUARED then
            isDragging.set(true)

        if isDragging.now() then
          event.preventDefault()
          lastPanOpt.foreach { lastPoint =>
            val panD = touchPoint - lastPoint
            EditorState.viewTransform.update(t =>
              t.copy(
                pan = t.pan + panD
              )
            )
            lastPanPoint.set(Some(touchPoint))
          }
      }
    else if touches.length == 2 then
      val initDistOpt     = initialTouchDistance.now()
      val initScaleOpt    = initialScale.now()
      val initAngleOpt    = initialAngle.now()
      val initRotationOpt = initialRotation.now()
      val anchorOpt       = pinchAnchorPoint.now()

      (initDistOpt, initScaleOpt, initAngleOpt, initRotationOpt, anchorOpt) match
        case (Some(initialDist), Some(initScale), Some(initAngle), Some(initRotation), Some(anchorPoint)) =>
          event.preventDefault()
          val touch1 = touches(0)
          val touch2 = touches(1)

          // New scale and rotation based on initial state
          val newDist       = getDistance(touch1, touch2)
          val newScale      = if (initialDist > 0) initScale * (newDist / initialDist) else initScale
          val newAngle      = getAngle(touch1, touch2)
          val rotationDelta = newAngle - initAngle
          val newRotation   = initRotation + rotationDelta

          // Calculate new pan to keep anchor point under the gesture center
          val transformedPoint = worldToScreenNoPan(anchorPoint, newScale, newRotation)

          val gestureCenterX = (touch1.clientX + touch2.clientX) / 2
          val gestureCenterY = (touch1.clientY + touch2.clientY) / 2

          EditorState.canvasElementRef.now().foreach { canvasElement =>
            val canvasRect = canvasElement.getBoundingClientRect()
            val pointerX   =
              (gestureCenterX - canvasRect.left) * (EditorConfig.canvasViewBoxWidth / canvasRect.width)
            val pointerY   =
              (gestureCenterY - canvasRect.top) * (EditorConfig.canvasViewBoxHeight / canvasRect.height)
            val pointer    =
              Point(pointerX, pointerY)

            val newPan = pointer - transformedPoint

            EditorState.viewTransform.update(_.copy(
              scale = newScale,
              pan = newPan
            ).withRotation(newRotation.toInt))
          }
        case _                                                                                            => // State wasn't correctly initialized

  def handleTouchEnd(event: TouchEvent): Unit =
    if isDragging.now() then
      event.preventDefault()
    resetTouchState()

  def handleTouchCancel(event: TouchEvent): Unit =
    resetTouchState()

  private def resetTouchState(): Unit =
    initialTouchDistance.set(None)
    initialScale.set(None)
    initialAngle.set(None)
    initialRotation.set(None)
    pinchAnchorPoint.set(None)
    lastPanPoint.set(None)
    touchStartPoint.set(None)
    isDragging.set(false)

  private def getDistance(touch1: dom.Touch, touch2: dom.Touch): Double =
    val dx = touch1.clientX - touch2.clientX
    val dy = touch1.clientY - touch2.clientY
    Math.sqrt(dx * dx + dy * dy)

  private def getAngle(touch1: dom.Touch, touch2: dom.Touch): Double =
    val dx = touch2.clientX - touch1.clientX
    val dy = touch2.clientY - touch1.clientY
    Math.toDegrees(Math.atan2(dy, dx))

  private def screenToWorld(screen: Point, transform: ViewTransform): Point =
    val (pan, scale, rotationDegrees) =
      (transform.pan, transform.scale, transform.rotationDegrees)
    val rotRad                        = Math.toRadians(rotationDegrees)
    val rotationCenter                = EditorConfig.canvasCenter

    // Inverse transform
    val p1 = (screen - pan) / scale
    val p2 = p1 - rotationCenter
    val p3 = p2.rotate(Radian(-rotRad))

    p3 + rotationCenter

  private def worldToScreenNoPan(
      world: Point,
      scale: Double,
      rotationDegrees: Double
  ): Point =
    val rotRad         = Math.toRadians(rotationDegrees)
    val rotationCenter = EditorConfig.canvasCenter

    // Forward transform (without pan)
    val p1 = world - rotationCenter
    val p2 = p1.rotate(Radian(rotRad))
    (p2 + rotationCenter) * scale
