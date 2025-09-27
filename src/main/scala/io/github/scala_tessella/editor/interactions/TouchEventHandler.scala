package io.github.scala_tessella.editor.interactions

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.models.{EditorConfig, EditorState, ViewTransform}
import io.github.scala_tessella.editor.utils.Geometry.Point2
import org.scalajs.dom
import org.scalajs.dom.TouchEvent

object TouchEventHandler:

  // For pinch-zoom and rotate
  private val initialTouchDistance = Var[Option[Double]](None)
  private val initialScale         = Var[Option[Double]](None)
  private val initialAngle         = Var[Option[Double]](None)
  private val initialRotation      = Var[Option[Double]](None)
  private val pinchAnchorPoint     = Var[Option[(Double, Double)]](None) // World coordinates

  // For single-touch pan
  private val lastPanPoint = Var[Option[(Double, Double)]](None)

  // To distinguish tap from drag
  private val touchStartPoint        = Var[Option[(Double, Double)]](None)
  private val isDragging             = Var[Boolean](false)
  private val DRAG_THRESHOLD_SQUARED = 25.0 // Using squared distance to avoid sqrt

  def handleTouchStart(event: TouchEvent): Unit =
    val touches = event.touches
    if touches.length == 1 then
      val touch = touches(0)
      val p     = (touch.clientX, touch.clientY)
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

        val worldPoint = screenToWorld(pointerX, pointerY, currentTransform)
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
        val touch = touches(0)
        if !dragging then
          val dx = touch.clientX - startPoint._1
          val dy = touch.clientY - startPoint._2
          if dx * dx + dy * dy > DRAG_THRESHOLD_SQUARED then
            isDragging.set(true)

        if isDragging.now() then
          event.preventDefault()
          lastPanOpt.foreach { lastPoint =>
            val panDx = touch.clientX - lastPoint._1
            val panDy = touch.clientY - lastPoint._2
            EditorState.viewTransform.update(t =>
              t.copy(
                panX = t.panX + panDx,
                panY = t.panY + panDy
              )
            )
            lastPanPoint.set(Some((touch.clientX, touch.clientY)))
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
          val (worldX, worldY) = anchorPoint
          val transformedPoint = worldToScreenNoPan(worldX, worldY, newScale, newRotation)

          val gestureCenterX = (touch1.clientX + touch2.clientX) / 2
          val gestureCenterY = (touch1.clientY + touch2.clientY) / 2

          EditorState.canvasElementRef.now().foreach { canvasElement =>
            val canvasRect = canvasElement.getBoundingClientRect()
            val pointerX   =
              (gestureCenterX - canvasRect.left) * (EditorConfig.canvasViewBoxWidth / canvasRect.width)
            val pointerY   =
              (gestureCenterY - canvasRect.top) * (EditorConfig.canvasViewBoxHeight / canvasRect.height)

            val newPanX = pointerX - transformedPoint._1
            val newPanY = pointerY - transformedPoint._2

            EditorState.viewTransform.update(_.copy(
              scale = newScale,
              panX = newPanX,
              panY = newPanY
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

  private def screenToWorld(screenX: Double, screenY: Double, transform: ViewTransform): (Double, Double) =
    val (panX, panY, scale, rotationDegrees) =
      (transform.panX, transform.panY, transform.scale, transform.rotationDegrees)
    val rotRad                               = Math.toRadians(rotationDegrees)
    val rotationCenter                       = EditorConfig.canvasCenter

    // Inverse transform
    val p1_x      = (screenX - panX) / scale
    val p1_y      = (screenY - panY) / scale
    val p2_x      = p1_x - rotationCenter.xx
    val p2_y      = p1_y - rotationCenter.yy
    val invRotRad = -rotRad
    val cosInvRot = Math.cos(invRotRad)
    val sinInvRot = Math.sin(invRotRad)
    val p3_x      = p2_x * cosInvRot - p2_y * sinInvRot
    val p3_y      = p2_x * sinInvRot + p2_y * cosInvRot
    val worldX    = p3_x + rotationCenter.xx
    val worldY    = p3_y + rotationCenter.yy
    (worldX, worldY)

  private def worldToScreenNoPan(
      worldX: Double,
      worldY: Double,
      scale: Double,
      rotationDegrees: Double
  ): (Double, Double) =
    val rotRad         = Math.toRadians(rotationDegrees)
    val rotationCenter = EditorConfig.canvasCenter

    // Forward transform (without pan)
    val cosRot = Math.cos(rotRad)
    val sinRot = Math.sin(rotRad)
    val p1_x   = worldX - rotationCenter.xx
    val p1_y   = worldY - rotationCenter.yy
    val p2_x   = p1_x * cosRot - p1_y * sinRot
    val p2_y   = p1_x * sinRot + p1_y * cosRot
    val p3_x   = (p2_x + rotationCenter.xx) * scale
    val p3_y   = (p2_y + rotationCenter.yy) * scale
    (p3_x, p3_y)
