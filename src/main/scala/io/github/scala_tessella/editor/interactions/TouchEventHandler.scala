package io.github.scala_tessella.editor.interactions

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.models.{EditorConfig, EditorState, Tool, ViewTransform}
import io.github.scala_tessella.editor.operations.{AddCopyOperations, ProximityQuery, ViewOperations}
import io.github.scala_tessella.editor.utils.geo.{LineSegment, Point, Radian}
import org.scalajs.dom
import org.scalajs.dom.{DOMRect, Touch, TouchEvent, TouchList}

object TouchEventHandler:

  // For pinch-zoom and rotate
  private val initialTouchDistance = Var[Option[Double]](None)
  private val initialScale         = Var[Option[Double]](None)
  private val initialAngle         = Var[Option[Radian]](None)
  private val initialRotation      = Var[Option[Radian]](None)
  private val pinchAnchorPoint     = Var[Option[Point]](None) // World coordinates

  // For single-touch pan
  private val lastPanPoint = Var[Option[Point]](None)

  // To distinguish tap from drag
  private val touchStartPoint        = Var[Option[Point]](None)
  private val isDragging             = Var[Boolean](false)
  private val DRAG_THRESHOLD_SQUARED = 25.0 // Using squared distance to avoid sqrt

  extension (touch: Touch)

    def toPoint: Point =
      Point(touch.clientX, touch.clientY)

  extension (domRect: DOMRect)

    def toSegment: LineSegment =
      LineSegment(
        Point(domRect.left, domRect.top),
        Point(domRect.width, domRect.height)
      )

  private def segmentFromTouchPair(touches: TouchList) =
    LineSegment(touches(0).toPoint, touches(1).toPoint)

  private def getPointer(canvasElement: dom.Element, segment: LineSegment) =
    val canvasRect    = canvasElement.getBoundingClientRect()
    val rect          = canvasRect.toSegment
    val gestureCenter = segment.midPoint
    (gestureCenter - rect.p1) * (EditorConfig.canvasEnd / rect.p2)

  def handleTouchStart(event: TouchEvent): Unit =
    val touches = event.touches
    if touches.length == 1 then
      val touch      = touches(0)
      val touchPoint = touch.toPoint
      // Add-Copy tools own the one-finger drag: grab the skeleton instead of panning the canvas
      // (matches MouseEventHandler). `preventDefault` suppresses scrolling and the synthetic mouse
      // events the browser would otherwise fire after `touchend`. Two-finger pinch/zoom stays live.
      if AddCopyOperations.beginDragForActiveTool(touchPoint.x, touchPoint.y) then
        event.preventDefault()
      else
        touchStartPoint.set(Some(touchPoint))
        lastPanPoint.set(Some(touchPoint))
        isDragging.set(false)
        // Eraser / Measurement proximity: show nearby points on first tap (ADR-013).
        val tool = EditorState.toolState.now().activeTool
        if tool == Tool.Eraser || tool == Tool.Measurement then
          ProximityQuery.updateNearbyPoints(touchPoint.x, touchPoint.y, isTouch = true)
    else if touches.length == 2 then
      event.preventDefault()
      isDragging.set(true)
      val segment = segmentFromTouchPair(touches)

      initialTouchDistance.set(Some(segment.length))
      initialAngle.set(Some(segment.horizontalAngle))

      val currentTransform = EditorState.viewState.now().viewTransform
      initialScale.set(Some(currentTransform.scale))
      initialRotation.set(Some(Radian.fromDegrees(currentTransform.rotationDegrees)))

      // Set an anchor point for zooming
      EditorState.uiState.now().canvasElementRef.foreach: canvasElement =>

        val pointer    = getPointer(canvasElement, segment)
        val worldPoint = screenToWorld(pointer, currentTransform)
        pinchAnchorPoint.set(Some(worldPoint))
    else
      isDragging.set(true)
      event.preventDefault()

  def handleTouchMove(event: TouchEvent): Unit =
    val touches = event.touches

    if touches.length == 1 && AddCopyOperations.hasActiveDrag then
      // One-finger move while an Add-Copy skeleton drag is in flight: track it, don't pan.
      event.preventDefault()
      val touchPoint = touches(0).toPoint
      val _          = AddCopyOperations.updateActiveDrag(touchPoint.x, touchPoint.y)
    else if touches.length == 1 then
      val startPointOpt = touchStartPoint.now()
      val dragging      = isDragging.now()
      val lastPanOpt    = lastPanPoint.now()

      startPointOpt.foreach: startPoint =>

        val touch      = touches(0)
        val touchPoint = touch.toPoint
        if !dragging then
          val drag = touchPoint - startPoint
          if drag.dot(drag) > DRAG_THRESHOLD_SQUARED then
            isDragging.set(true)

        if isDragging.now() then
          event.preventDefault()
          lastPanOpt.foreach: lastPoint =>

            val panD = touchPoint - lastPoint
            EditorState.viewState.update: s =>

              val vt = s.viewTransform
              s.copy(viewTransform = vt.copy(pan = vt.pan + panD))
            lastPanPoint.set(Some(touchPoint))
    else if touches.length == 2 then
      val initDistOpt     = initialTouchDistance.now()
      val initScaleOpt    = initialScale.now()
      val initAngleOpt    = initialAngle.now()
      val initRotationOpt = initialRotation.now()
      val anchorOpt       = pinchAnchorPoint.now()

      (initDistOpt, initScaleOpt, initAngleOpt, initRotationOpt, anchorOpt) match
        case (Some(initialDist), Some(initScale), Some(initAngle), Some(initRotation), Some(anchorPoint)) =>
          event.preventDefault()
          val segment = segmentFromTouchPair(touches)

          // New scale and rotation based on the initial state
          val newDistance   = segment.length
          val rawScale      = if initialDist > 0 then initScale * (newDistance / initialDist) else initScale
          val newScale      = ViewOperations.clampViewScale(rawScale)
          val newAngle      = segment.horizontalAngle.normalize
          val rotationDelta = newAngle - initAngle
          val newRotation   = initRotation + rotationDelta

          // Calculate new pan to keep anchor point under the gesture center
          val transformedPoint = worldToScreenNoPan(anchorPoint, newScale, newRotation)

          val gestureCenter = segment.midPoint

          EditorState.uiState.now().canvasElementRef.foreach: canvasElement =>

            val pointer = getPointer(canvasElement, segment)
            val newPan  = pointer - transformedPoint
            EditorState.viewState.update(_.copy(viewTransform =
              EditorState.viewState.now().viewTransform
                .copy(scale = newScale, pan = newPan)
                .withRotation(newRotation.toDegrees.toInt)
            ))
        case _                                                                                            => // State wasn't correctly initialized

  def handleTouchEnd(event: TouchEvent): Unit =
    // Finish an Add-Copy skeleton drag first (commits the weld if a target was snapped).
    if AddCopyOperations.hasActiveDrag then
      event.preventDefault()
      val _ = AddCopyOperations.endActiveDrag()
    else
      val wasDragging = isDragging.now()
      if wasDragging then
        event.preventDefault()
      else
        // Tap (not drag) while Eraser or Measurement is active: route a direct-hit to the
        // tool's point handler — delete for Eraser, start/end for Measurement (ADR-013).
        val tool = EditorState.toolState.now().activeTool
        if tool == Tool.Eraser || tool == Tool.Measurement then
          touchStartPoint.now().foreach: startPoint =>
            ProximityQuery.findDirectHit(startPoint.x, startPoint.y).foreach: hit =>

              val ops = io.github.scala_tessella.editor.operations.SelectionOperations
              tool match
                case Tool.Measurement => ops.handlePointClickForMeasurement(hit)
                case _                => ops.handlePointClickForDeletion(hit)
    resetTouchState()

  def handleTouchCancel(event: TouchEvent): Unit =
    // Interrupted gesture: drop any in-flight Add-Copy drag without committing.
    if AddCopyOperations.hasActiveDrag then
      AddCopyOperations.cancelDrag()
    resetTouchState()

  private def resetTouchState(): Unit =
    EditorState.previewState.update(_.copy(previewPlacement = None))
    initialTouchDistance.set(None)
    initialScale.set(None)
    initialAngle.set(None)
    initialRotation.set(None)
    pinchAnchorPoint.set(None)
    lastPanPoint.set(None)
    touchStartPoint.set(None)
    isDragging.set(false)

  private def screenToWorld(screen: Point, transform: ViewTransform): Point =
    val (pan, scale, rotationDegrees) =
      (transform.pan, transform.scale, transform.rotationDegrees)
    val rotRad                        = Radian.fromDegrees(rotationDegrees)
    val rotationCenter                = EditorConfig.canvasCenter

    // Inverse transform
    val p1 = (screen - pan) / scale
    val p2 = p1 - rotationCenter
    val p3 = p2.rotate(rotRad * -1)

    p3 + rotationCenter

  private def worldToScreenNoPan(
      world: Point,
      scale: Double,
      rotation: Radian
  ): Point =
    val rotationCenter = EditorConfig.canvasCenter

    // Forward transform (without pan)
    val p1 = world - rotationCenter
    val p2 = p1.rotate(rotation)
    (p2 + rotationCenter) * scale
