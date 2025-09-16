package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.utils.TessellationGeometry.*
import io.github.scala_tessella.dcel.{TilingDCEL, VertexId}
import io.github.scala_tessella.dcel.BigDecimalGeometry.BigPoint
import io.github.scala_tessella.tessella.Geometry.Point
import io.github.scala_tessella.tessella.Topology.{Node as TilingNode}
import io.github.scala_tessella.tessella.BigDecimalGeometry.{AngleDegree, BigCoords}

import scala.math.*

/**
 * Shared geometry utilities to compute the exact placement (screen points) of a regular polygon
 * attached to a given perimeter edge.
 */
object PolygonPlacementGeometry:

  extension (bigPoint: BigPoint)
    def toPoint: Point =
      Point(bigPoint.x.toDouble, bigPoint.y.toDouble)

  /** Compute canvas coordinates of the wireframe points for a placement preview/failure. */
  def computeWireframePoints(polygonSides: Int, edge: (VertexId, VertexId), tiling: TilingDCEL): Vector[(Double, Double)] =
    val vertex1 = tiling.coordinates(edge._1).toPoint
    val vertex2 = tiling.coordinates(edge._2).toPoint

    // Calculate edge vector and length
    val edgeVectorX = vertex2.x - vertex1.x
    val edgeVectorY = vertex2.y - vertex1.y
    val edgeLength = sqrt(edgeVectorX * edgeVectorX + edgeVectorY * edgeVectorY)

    // Normalize edge vector
    val edgeUnitX = edgeVectorX / edgeLength
    val edgeUnitY = edgeVectorY / edgeLength

    // Outward direction and potential flip info
    val ((perpX, perpY), wasFlipped) = calculateOutwardDirectionWithFlipInfo(edge, tiling)

    val halfAngleStep: AngleDegree = AngleDegree(180) / polygonSides
    val sideLength = edgeLength
    val apothem = sideLength / (2 * tan(halfAngleStep.toBigRadian.toBigDecimal.toDouble))

    // Center from edge midpoint shifted outward by apothem
    val centerX = (vertex1.x + vertex2.x) / 2 + perpX * apothem
    val centerY = (vertex1.y + vertex2.y) / 2 + perpY * apothem

    // Generate polygon vertices
    val angleStep: AngleDegree = halfAngleStep * 2
    val radius = sideLength / (2 * sin(halfAngleStep.toBigRadian.toBigDecimal.toDouble))
    val winding = if wasFlipped then -1 else 1
    val edgeAngle = atan2(edgeUnitY, edgeUnitX)
    val isOdd = polygonSides % 2 == 1

    val baseOffset: AngleDegree = AngleDegree(90) - halfAngleStep
    val oddHalfStep = if isOdd && !wasFlipped then halfAngleStep else AngleDegree(0)
    val startAngle = edgeAngle + (baseOffset + oddHalfStep).toBigRadian.toBigDecimal.toDouble

    (0 until polygonSides).map { i =>
      val angle = startAngle + (angleStep * winding * i).toBigRadian.toBigDecimal.toDouble
      val x = centerX + radius * cos(angle)
      val y = centerY + radius * sin(angle)
      tilingPointToCanvasView(Point(x, y))
    }.toVector

  /** Decide outward normal from an edge, fall back safely if context missing. */
  private def calculateOutwardDirectionWithFlipInfo(edge: (VertexId, VertexId), tiling: TilingDCEL): ((Double, Double), Boolean) =
    try
      val v1 = tiling.coordinates(edge._1).toPoint
      val v2 = tiling.coordinates(edge._2).toPoint

      val ex = v2.x - v1.x
      val ey = v2.y - v1.y
      val len = sqrt(ex * ex + ey * ey)

      // Two perpendicular directions
      val perp1X = -ey / len
      val perp1Y =  ex / len
      val perp2X =  ey / len
      val perp2Y = -ex / len

      // Midpoint
      val midX = (v1.x + v2.x) / 2
      val midY = (v1.y + v2.y) / 2

      // Small test points
      val testPoint1 = Point(midX + perp1X * 0.5, midY + perp1Y * 0.5)
      val testPoint2 = Point(midX + perp2X * 0.5, midY + perp2Y * 0.5)

      // If boundary info or containing polygon is unavailable, default to perp1 outward without flip.
      // Implement polygon containment here if/when you expose oriented polygons from DCEL.
      ((perp1X, perp1Y), false)
    catch
      case _: Throwable =>
        val v1 = tiling.coordinates(edge._1).toPoint
        val v2 = tiling.coordinates(edge._2).toPoint
        val ex = v2.x - v1.x
        val ey = v2.y - v1.y
        val len = sqrt(ex * ex + ey * ey)
        ((-ey / len, ex / len), false)
