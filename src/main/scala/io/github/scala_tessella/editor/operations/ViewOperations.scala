package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.models.EditorConfig.*
import io.github.scala_tessella.editor.utils.TessellationGeometry.maybeBounds

import io.github.scala_tessella.tessella.Geometry.Point

object ViewOperations:

  def fitTilingToCanvas(): Unit =
    val tiling = EditorState.currentTiling.now()
    if tiling.isEmpty then return

    val coords = tiling.coordinates.values.map(p => (p.x * canvasScale, p.y * canvasScale))
    if coords.isEmpty then return

    EditorState.canvasElementRef.now().foreach { canvasElement =>
      val canvasRect = canvasElement.getBoundingClientRect()
      val canvasWidth = canvasRect.width
      val canvasHeight = canvasRect.height
      val currentTransform = EditorState.viewTransform.now()

      if canvasWidth > 0 && canvasHeight > 0 then
        val rad = currentTransform.rotationDegrees * Math.PI / 180
        val cosRad = Math.cos(rad)
        val sinRad = Math.sin(rad)

        val rotatedCoords = coords.map { case (x, y) =>
          Point(x * cosRad - y * sinRad, x * sinRad + y * cosRad)
        }

        val bounds = rotatedCoords.toList.maybeBounds.get

        val tilingWidth = bounds.maxX - bounds.minX
        val tilingHeight = bounds.maxY - bounds.minY

        if tilingWidth > 0 || tilingHeight > 0 then
          val padding = 40.0

          val safeTilingWidth = if tilingWidth <= 0 then 1 else tilingWidth
          val safeTilingHeight = if tilingHeight <= 0 then 1 else tilingHeight

          val scaleX = (canvasWidth - padding * 2) / safeTilingWidth
          val scaleY = (canvasHeight - padding * 2) / safeTilingHeight
          val newScale = Math.min(scaleX, scaleY)

          val tilingCenterX = (bounds.minX + bounds.maxX) / 2.0
          val tilingCenterY = (bounds.minY + bounds.maxY) / 2.0

          val tilingCenterOnCanvasX = tilingCenterX + canvasCenterX
          val tilingCenterOnCanvasY = tilingCenterY + canvasCenterY

          val newPanX = canvasWidth / 2.0 - tilingCenterOnCanvasX * newScale
          val newPanY = canvasHeight / 2.0 - tilingCenterOnCanvasY * newScale

          EditorState.viewTransform.set(
            currentTransform.copy(
              scale = newScale,
              panX = newPanX,
              panY = newPanY
            )
          )
    }

  def rotateView(delta: Double): Unit =
    val currentTransform = EditorState.viewTransform.now()
    val scale = currentTransform.scale
    val panX = currentTransform.panX
    val panY = currentTransform.panY
    val rotationRad = currentTransform.rotationDegrees * Math.PI / 180

    val viewCenterX = canvasCenterX
    val viewCenterY = canvasCenterY

    // Inverse transform of view center to get world point
    val p_after_inv_pan_x = viewCenterX - panX
    val p_after_inv_pan_y = viewCenterY - panY

    val p_after_inv_scale_x = p_after_inv_pan_x / scale
    val p_after_inv_scale_y = p_after_inv_pan_y / scale

    val cos_inv_rot = Math.cos(-rotationRad)
    val sin_inv_rot = Math.sin(-rotationRad)

    val p_intermediate_x = p_after_inv_scale_x - viewCenterX
    val p_intermediate_y = p_after_inv_scale_y - viewCenterY

    val world_x = viewCenterX + p_intermediate_x * cos_inv_rot - p_intermediate_y * sin_inv_rot
    val world_y = viewCenterY + p_intermediate_x * sin_inv_rot + p_intermediate_y * cos_inv_rot

    // Calculate new pan with new rotation
    val newRotationDegrees = currentTransform.rotationDegrees + delta
    val newRotationRad = newRotationDegrees * Math.PI / 180

    // Forward transform the world point with new rotation
    val cos_new_rot = Math.cos(newRotationRad)
    val sin_new_rot = Math.sin(newRotationRad)

    val p_intermediate2_x = world_x - viewCenterX
    val p_intermediate2_y = world_y - viewCenterY

    val p_after_rot_x = viewCenterX + p_intermediate2_x * cos_new_rot - p_intermediate2_y * sin_new_rot
    val p_after_rot_y = viewCenterY + p_intermediate2_x * sin_new_rot + p_intermediate2_y * cos_new_rot

    val p_after_scale_x = p_after_rot_x * scale
    val p_after_scale_y = p_after_rot_y * scale

    val newPanX = viewCenterX - p_after_scale_x
    val newPanY = viewCenterY - p_after_scale_y

    EditorState.viewTransform.set(
      currentTransform.withRotation(newRotationDegrees).copy(
        panX = newPanX,
        panY = newPanY
      )
    )