package art.tessell.editor.utils.geo

import io.github.scala_tessella.dcel.geometry.BigPoint
import art.tessell.editor.models.EditorConfig.*

object TessellationGeometry:

  /** Transforms a point from tiling coordinates to canvas view coordinates.
    *
    * @param point
    *   The Point in tiling coordinates.
    * @return
    *   The transformed point on the canvas.
    */
  def tilingPointToCanvasView(point: Point): Point =
    point.scaleAndTranslate(canvasScale, canvasCenter)

  extension (bigPoint: BigPoint)

    def toPoint: Point =
      Point(bigPoint.x.toDouble, bigPoint.y.toDouble)
