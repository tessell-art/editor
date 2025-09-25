package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.BigDecimalGeometry.AngleDegree
import io.github.scala_tessella.editor.models.EditorConfig._
import io.github.scala_tessella.editor.models.{EditorState, ViewTransform}
import io.github.scala_tessella.editor.utils.Geometry.{Bounds, Point, Radian}
import io.github.scala_tessella.editor.utils.TessellationGeometry._

object ViewOperations:

  // Pure function to transform coordinates based on rotation
  private[operations] def transformCoordinates(coords: Iterable[Point], rotationDegrees: Int): List[Point] =
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
    val tilingCenterX = (bounds.minX + bounds.maxX) / 2.0
    val tilingCenterY = (bounds.minY + bounds.maxY) / 2.0
    (tilingCenterX, tilingCenterY)

  // Pure function to calculate new pan values to center the tiling
  private[operations] def calculateNewPan(
      canvasWidth: Double,
      canvasHeight: Double,
      tilingCenterX: Double,
      tilingCenterY: Double,
      newScale: Double
  ): (Double, Double) =
    val tilingCenterOnCanvasX = tilingCenterX + canvasCenterX
    val tilingCenterOnCanvasY = tilingCenterY + canvasCenterY

    val newPanX = canvasWidth / 2.0 - tilingCenterOnCanvasX * newScale
    val newPanY = canvasHeight / 2.0 - tilingCenterOnCanvasY * newScale

    (newPanX, newPanY)

  // Pure function to create a new ViewTransform with updated values
  private[operations] def createUpdatedViewTransform(
      currentTransform: ViewTransform,
      newScale: Double,
      newPanX: Double,
      newPanY: Double
  ): ViewTransform =
    currentTransform.copy(
      scale = newScale,
      panX = newPanX,
      panY = newPanY
    )

  // Pure function to calculate the new view transform to fit the tiling to the canvas
  private[operations] def calculateFitToCanvasTransform(
      coords: Iterable[Point],
      canvasWidth: Double,
      canvasHeight: Double,
      currentTransform: ViewTransform,
      padding: Double
  ): Option[ViewTransform] =
    // Use the viewBox dimensions for calculation, not the canvas element's actual dimensions,
    // because the pan and scale are applied within the SVG's viewBox coordinate system.
    val viewBoxWidth  = canvasCenterX * 2.0
    val viewBoxHeight = canvasCenterY * 2.0

    if (viewBoxWidth <= 0 || viewBoxHeight <= 0) return None

    val rotatedCoords = transformCoordinates(coords, currentTransform.rotationDegrees)
    rotatedCoords.maybeBounds.flatMap { bounds =>
      val tilingWidth  = bounds.maxX - bounds.minX
      val tilingHeight = bounds.maxY - bounds.minY

      if tilingWidth <= 0 && tilingHeight <= 0 then None
      else
        val newScale                       = calculateNewScale(viewBoxWidth, viewBoxHeight, tilingWidth, tilingHeight, padding)
        val (tilingCenterX, tilingCenterY) = calculateTilingCenter(bounds)
        val (newPanX, newPanY)             =
          calculateNewPan(viewBoxWidth, viewBoxHeight, tilingCenterX, tilingCenterY, newScale)
        Some(createUpdatedViewTransform(currentTransform, newScale, newPanX, newPanY))
    }

  def fitTilingToCanvas(): Unit =
    val tiling = EditorState.currentTiling.now()
    if tiling.isEmpty then return

    val coords = tiling.boundaryVertices.map(_.coords.toPoint).map(_.scale(canvasScale))
    if coords.isEmpty then return

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
      viewCenterX: Double,
      viewCenterY: Double,
      panX: Double,
      panY: Double,
      scale: Double,
      rotationRad: Double
  ): (Double, Double) =
    // Inverse pan
    val afterInvPanX = viewCenterX - panX
    val afterInvPanY = viewCenterY - panY

    // Inverse scale
    val afterInvScaleX = afterInvPanX / scale
    val afterInvScaleY = afterInvPanY / scale

    // Inverse rotation
    val cosInvRot = Math.cos(-rotationRad)
    val sinInvRot = Math.sin(-rotationRad)

    val intermediateX = afterInvScaleX - viewCenterX
    val intermediateY = afterInvScaleY - viewCenterY

    val worldX = viewCenterX + intermediateX * cosInvRot - intermediateY * sinInvRot
    val worldY = viewCenterY + intermediateX * sinInvRot + intermediateY * cosInvRot

    (worldX, worldY)

  // Pure function to perform forward transformation (world to screen)
  private[operations] def forwardTransform(
      worldX: Double,
      worldY: Double,
      viewCenterX: Double,
      viewCenterY: Double,
      scale: Double,
      rotationRad: Double
  ): (Double, Double) =
    // Forward rotation
    val cosRot = Math.cos(rotationRad)
    val sinRot = Math.sin(rotationRad)

    val intermediateX = worldX - viewCenterX
    val intermediateY = worldY - viewCenterY

    val afterRotX = viewCenterX + intermediateX * cosRot - intermediateY * sinRot
    val afterRotY = viewCenterY + intermediateX * sinRot + intermediateY * cosRot

    // Forward scale
    val afterScaleX = afterRotX * scale
    val afterScaleY = afterRotY * scale

    (afterScaleX, afterScaleY)

  // Pure function to calculate new pan values after rotation
  private[operations] def calculateRotatedPan(
      worldX: Double,
      worldY: Double,
      viewCenterX: Double,
      viewCenterY: Double,
      scale: Double,
      newRotationRad: Double
  ): (Double, Double) =
    val (afterScaleX, afterScaleY) = forwardTransform(
      worldX,
      worldY,
      viewCenterX,
      viewCenterY,
      scale,
      newRotationRad
    )

    val newPanX = viewCenterX - afterScaleX
    val newPanY = viewCenterY - afterScaleY

    (newPanX, newPanY)

  def rotateView(delta: Int): Unit =
    val currentTransform = EditorState.viewTransform.now()
    val scale            = currentTransform.scale
    val panX             = currentTransform.panX
    val panY             = currentTransform.panY
    val rotationRad      = AngleDegree(currentTransform.rotationDegrees).toBigRadian.toBigDecimal.toDouble

    val viewCenterX = canvasCenterX
    val viewCenterY = canvasCenterY

    // Convert view center to world coordinates
    val (worldX, worldY) = inverseTransform(
      viewCenterX,
      viewCenterY,
      panX,
      panY,
      scale,
      rotationRad
    )

    // Calculate new rotation
    val newRotationDegrees = currentTransform.rotationDegrees + delta
    val newRotationRad     = AngleDegree(newRotationDegrees).toBigRadian.toBigDecimal.toDouble

    // Calculate new pan values
    val (newPanX, newPanY) = calculateRotatedPan(
      worldX,
      worldY,
      viewCenterX,
      viewCenterY,
      scale,
      newRotationRad
    )

    // Update the view transform
    EditorState.viewTransform.set(
      currentTransform.withRotation(newRotationDegrees).copy(
        panX = newPanX,
        panY = newPanY
      )
    )
