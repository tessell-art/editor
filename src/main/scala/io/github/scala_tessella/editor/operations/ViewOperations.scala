package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.EditorState

object ViewOperations:

  def fitTilingToCanvas(): Unit =
    val tiling = EditorState.currentTiling.now()
    if tiling.isEmpty then return

    val coords = tiling.coordinates.values.map(p => (p.x * 50, p.y * 50))
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
          (x * cosRad - y * sinRad, x * sinRad + y * cosRad)
        }

        val minX = rotatedCoords.map(_._1).min
        val maxX = rotatedCoords.map(_._1).max
        val minY = rotatedCoords.map(_._2).min
        val maxY = rotatedCoords.map(_._2).max

        val tilingWidth = maxX - minX
        val tilingHeight = maxY - minY

        if tilingWidth > 0 || tilingHeight > 0 then
          val padding = 40.0

          val safeTilingWidth = if tilingWidth <= 0 then 1 else tilingWidth
          val safeTilingHeight = if tilingHeight <= 0 then 1 else tilingHeight

          val scaleX = (canvasWidth - padding * 2) / safeTilingWidth
          val scaleY = (canvasHeight - padding * 2) / safeTilingHeight
          val newScale = Math.min(scaleX, scaleY)

          val tilingCenterX = (minX + maxX) / 2.0
          val tilingCenterY = (minY + maxY) / 2.0

          val tilingCenterOnCanvasX = tilingCenterX + 400
          val tilingCenterOnCanvasY = tilingCenterY + 300

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
