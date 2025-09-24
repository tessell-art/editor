package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.BigDecimalGeometry.BigPoint

object DualTessellation:

  /**
   * Generates the line segments for the dual tessellation representation.
   *
   * For each polygon in the tiling, it calculates lines from the polygon's center
   * to the midpoint of each of its edges.
   *
   * @param tiling The tiling for which to generate dual lines.
   * @return A sequence of pairs of `BigPoint`, where each pair represents a line segment (midpoint, center).
   */
  def generateDualLines(tiling: TilingDCEL): List[(BigPoint, BigPoint)] =
    tiling.innerFacesVertices.flatMap { (_, faceVertices) =>
      val points = faceVertices.map(_.coords)
      if points.size < 2 then List.empty
      else
        val center = BigPoint(
          points.map(_.x).sum / points.size,
          points.map(_.y).sum / points.size
        )
        val edges = (points :+ points.head).sliding(2)

        edges.collect {
          case Seq(p1, p2) =>
            val midPoint = BigPoint((p1.x + p2.x) / 2, (p1.y + p2.y) / 2)
            (midPoint, center)
        }.toList
    }
