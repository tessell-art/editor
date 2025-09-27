package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.BigDecimalGeometry.AngleDegree
import io.github.scala_tessella.editor.models.EditorConfig._
import io.github.scala_tessella.editor.models.{EditorState, ViewTransform}
import io.github.scala_tessella.editor.utils.Geometry.{Bounds, Point2, Radian}
import io.github.scala_tessella.editor.utils.TessellationGeometry._

object ViewOperations:

  // Pure function to transform coordinates based on rotation
  private[operations] def transformCoordinates(coords: Iterable[Point2], rotationDegrees: Int): List[Point2] =
    val rotationRad = AngleDegree(rotationDegrees).toBigRadian.toBigDecimal.toDouble
    coords.map(_.rotate(Radian(rotationRad))).toList

  // Pure function to calculate safe tiling dimensions
  private[operations] def calculateSafeDimensions(
      tilingWidth: Double,
      tilingHeight: Double
  ): (Double, Double) =
    val safeTilingWidth  = if tilingWidth <= 0 then 1 else tilingWidth
    val safeTilingHeight = if tilingHeight <= 0 then 1 else tilingHeight
    (safeTilingWidth, safeTilingHeight)

  // Pure function to calculate new scale based on canvas and tiling dimensions
  private[operations] def calculateNewScale(
      canvasWidth: Double,
      canvasHeight: Double,
      tilingWidth: Double,
      tilingHeight: Double,
      padding: Double
  ): Double =
    val (safeTilingWidth, safeTilingHeight) = calculateSafeDimensions(tilingWidth, tilingHeight)
    val scaleX                              = (canvasWidth - padding * 2) / safeTilingWidth
    val scaleY                              = (canvasHeight - padding * 2) / safeTilingHeight
    Math.min(scaleX, scaleY)

  // Pure function to calculate tiling center
  private[operations] def calculateTilingCenter(bounds: Bounds): (Double, Double) =
    val c = bounds.center
    (c.xx, c.yy)

  // Pure function to calculate new pan values to center the tiling
  private[operations] def calculateNewPan(
      canvas: Point2,
      tilingCenter: Point2,
      newScale: Double
  ): Point2 =
    val tilingCenterOnCanvas = tilingCenter + canvasCenter
    canvas / 2.0 - tilingCenterOnCanvas * newScale

  // Pure function to create a new ViewTransform with updated values
  private[operations] def createUpdatedViewTransform(
      currentTransform: ViewTransform,
      newScale: Double,
      newPan: Point2
  ): ViewTransform =
    currentTransform.copy(
      scale = newScale,
      pan = newPan
    )

  // Pure function to calculate the new view transform to fit the tiling to the canvas
  private[operations] def calculateFitToCanvasTransform(
      coords: Iterable[Point2],
      canvasWidth: Double,
      canvasHeight: Double,
      currentTransform: ViewTransform,
      padding: Double
  ): Option[ViewTransform] =
    // Use the viewBox dimensions for calculation, not the canvas element's actual dimensions,
    // because the pan and scale are applied within the SVG's viewBox coordinate system.
    val viewBoxWidth  = canvasCenter.xx * 2.0
    val viewBoxHeight = canvasCenter.yy * 2.0

    if viewBoxWidth <= 0 || viewBoxHeight <= 0 then
      None
    else
      val rotatedCoords = transformCoordinates(coords, currentTransform.rotationDegrees)
      rotatedCoords.maybeBounds.flatMap { bounds =>
        val tilingWidth  = bounds.width
        val tilingHeight = bounds.height

        if tilingWidth <= 0 && tilingHeight <= 0 then None
        else
          val newScale                       = calculateNewScale(viewBoxWidth, viewBoxHeight, tilingWidth, tilingHeight, padding)
          val (tilingCenterX, tilingCenterY) = calculateTilingCenter(bounds)
          val newPan                         =
            calculateNewPan(
              Point2(viewBoxWidth, viewBoxHeight),
              Point2(tilingCenterX, tilingCenterY),
              newScale
            )
          Some(createUpdatedViewTransform(currentTransform, newScale, newPan))
      }

  def fitTilingToCanvas(): Unit =
    val tiling = EditorState.currentTiling.now()
    if !tiling.isEmpty then

      val coords = tiling.boundaryVertices.map(_.coords.toPoint).map(_.scale(canvasScale))
      if coords.nonEmpty then

        EditorState.canvasElementRef.now().foreach { canvasElement =>
          val canvasRect       = canvasElement.getBoundingClientRect()
          val currentTransform = EditorState.viewTransform.now()

          calculateFitToCanvasTransform(
            coords,
            canvasRect.width,
            canvasRect.height,
            currentTransform,
            padding = 40.0
          ).foreach(EditorState.viewTransform.set)
        }

  // Pure function to perform inverse transformation (screen to world)
  private[operations] def inverseTransform(
      viewCenter: Point2,
      pan: Point2,
      scale: Double,
      rotationRad: Double
  ): Point2 =
    // Inverse pan
    val afterInvPan = viewCenter - pan

    // Inverse scale
    val afterInvScale = afterInvPan / scale

    // Inverse rotation
    val cosInvRot = Math.cos(-rotationRad)
    val sinInvRot = Math.sin(-rotationRad)

    val intermediate = afterInvScale - viewCenter

    val worldX = viewCenter.xx + intermediate.xx * cosInvRot - intermediate.yy * sinInvRot
    val worldY = viewCenter.yy + intermediate.xx * sinInvRot + intermediate.yy * cosInvRot

    Point2(worldX, worldY)

  // Pure function to perform forward transformation (world to screen)
  private[operations] def forwardTransform(
      world: Point2,
      viewCenter: Point2,
      scale: Double,
      rotationRad: Double
  ): Point2 =
    // Forward rotation
    val cosRot = Math.cos(rotationRad)
    val sinRot = Math.sin(rotationRad)

    val intermediate = world - viewCenter

    val afterRotX = viewCenter.xx + intermediate.xx * cosRot - intermediate.yy * sinRot
    val afterRotY = viewCenter.yy + intermediate.xx * sinRot + intermediate.yy * cosRot

    // Forward scale
    Point2(afterRotX, afterRotY) * scale

  // Pure function to calculate new pan values after rotation
  private[operations] def calculateRotatedPan(
      world: Point2,
      viewCenter: Point2,
      scale: Double,
      newRotationRad: Double
  ): Point2 =
    val afterScale = forwardTransform(
      world,
      viewCenter,
      scale,
      newRotationRad
    )

    viewCenter - afterScale

  def rotateView(delta: Int): Unit =
    val currentTransform = EditorState.viewTransform.now()
    val scale            = currentTransform.scale
    val pan              = currentTransform.pan
    val rotationRad      = AngleDegree(currentTransform.rotationDegrees).toBigRadian.toBigDecimal.toDouble

    val viewCenter = canvasCenter

    // Convert view center to world coordinates
    val world = inverseTransform(
      viewCenter,
      pan,
      scale,
      rotationRad
    )

    // Calculate new rotation
    val newRotationDegrees = currentTransform.rotationDegrees + delta
    val newRotationRad     = AngleDegree(newRotationDegrees).toBigRadian.toBigDecimal.toDouble

    // Calculate new pan values
    val newPan = calculateRotatedPan(
      world,
      viewCenter,
      scale,
      newRotationRad
    )

    // Update the view transform
    EditorState.viewTransform.set(
      currentTransform.withRotation(newRotationDegrees).copy(
        pan = newPan
      )
    )
