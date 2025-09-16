package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.utils.TessellationGeometry.*
import io.github.scala_tessella.dcel.{FaceId, TilingDCEL, VertexId}
import io.github.scala_tessella.dcel.BigDecimalGeometry.BigPoint
import io.github.scala_tessella.tessella.Geometry.Point
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
  def computeWireframePoints(polygonSides: Int, edge: (VertexId, VertexId), tiling: TilingDCEL, intoFace: Option[FaceId] = None): Vector[(Double, Double)] =
    val vertex1 = tiling.coordinates(edge._1).toPoint
    val vertex2 = tiling.coordinates(edge._2).toPoint
  
    // Edge vector and length
    val ex = vertex2.x - vertex1.x
    val ey = vertex2.y - vertex1.y
    val edgeLen = sqrt(ex * ex + ey * ey)
    val ux = ex / edgeLen
    val uy = ey / edgeLen
  
    // Two perpendicular directions (unit)
    val perp1 = (-ey / edgeLen, ex / edgeLen)
    val perp2 = (ey / edgeLen, -ex / edgeLen)
  
    // Midpoint of the edge
    val midX = (vertex1.x + vertex2.x) / 2
    val midY = (vertex1.y + vertex2.y) / 2
  
    // If we know the face to go into, choose the perpendicular pointing toward the face center
    val (perpX, perpY, wasFlipped) = intoFace match
      case Some(fid) =>
        val face = tiling.findInnerFace(fid).toOption.get
        val vs = face.getVertices.toOption.get
        val center = {
          val pts = vs.map(_.coords).map(_.toPoint)
          val cx = pts.map(_.x).sum / pts.size
          val cy = pts.map(_.y).sum / pts.size
          Point(cx, cy)
        }
        val toCenter = (center.x - midX, center.y - midY)
        val dot1 = perp1._1 * toCenter._1 + perp1._2 * toCenter._2
        if dot1 >= 0 then (perp1._1, perp1._2, false) else (perp2._1, perp2._2, true)
      case None =>
        // Fallback: default to perp1 (matches boundary behavior)
        (perp1._1, perp1._2, false)
  
    val halfAngle: AngleDegree = AngleDegree(180) / polygonSides
    val sideLength = edgeLen
    val apothem = sideLength / (2 * tan(halfAngle.toBigRadian.toBigDecimal.toDouble))
  
    // Center shifted along chosen perpendicular
    val cx = midX + perpX * apothem
    val cy = midY + perpY * apothem
  
    // Vertex generation
    val angleStep: AngleDegree = halfAngle * 2
    val radius = sideLength / (2 * sin(halfAngle.toBigRadian.toBigDecimal.toDouble))
    val winding = if wasFlipped then -1 else 1
    val edgeAngle = atan2(uy, ux)
    val isOdd = polygonSides % 2 == 1
  
    val baseOffset: AngleDegree = AngleDegree(90) - halfAngle
    val oddHalfStep = if isOdd && !wasFlipped then halfAngle else AngleDegree(0)
    val startAngle = edgeAngle + (baseOffset + oddHalfStep).toBigRadian.toBigDecimal.toDouble
  
    (0 until polygonSides).map { i =>
      val a = startAngle + (angleStep * winding * i).toBigRadian.toBigDecimal.toDouble
      val px = cx + radius * cos(a)
      val py = cy + radius * sin(a)
      tilingPointToCanvasView(Point(px, py))
    }.toVector
