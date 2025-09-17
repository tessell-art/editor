package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.dcel.BigDecimalGeometry.BigPoint
import io.github.scala_tessella.editor.models.EditorConfig.*
import io.github.scala_tessella.tessella.Geometry.Point

case class Bounds(minX: Double, maxX: Double, minY: Double, maxY: Double)

object TessellationGeometry:

  /**
   * Transforms a point from tiling coordinates to canvas view coordinates.
   *
   * @param p The Point in tiling coordinates.
   * @return A tuple (Double, Double) representing the (x, y) on the canvas.
   */
  def tilingPointToCanvasView(p: Point): (Double, Double) =
    (p.x * canvasScale + canvasCenterX, p.y * canvasScale + canvasCenterY)

  extension (bigPoint: BigPoint)

    def toPoint: Point =
      Point(bigPoint.x.toDouble, bigPoint.y.toDouble)

  extension (points: Seq[Point])

    def maybeBounds: Option[Bounds] =
      if points.isEmpty then None
      else
        val minX = points.map(_.x).min
        val maxX = points.map(_.x).max
        val minY = points.map(_.y).min
        val maxY = points.map(_.y).max
        Some(Bounds(minX, maxX, minY, maxY))
