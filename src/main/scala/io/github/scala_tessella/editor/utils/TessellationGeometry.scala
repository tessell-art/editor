package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.dcel.BigDecimalGeometry.BigPoint
import io.github.scala_tessella.editor.models.EditorConfig._

object TessellationGeometry:

  /** Transforms a point from tiling coordinates to canvas view coordinates.
    *
    * @param point
    *   The Point in tiling coordinates.
    * @return
    *   The transformed point on the canvas.
    */
  def tilingPointToCanvasView(point: Point): Point =
    point.transform(canvasScale, canvasCenter)

  extension (bigPoint: BigPoint)

    def toPoint: Point =
      Point(bigPoint.x.toDouble, bigPoint.y.toDouble)
