package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.utils.TessellationGeometry.*
import io.github.scala_tessella.dcel.{FaceId, TilingDCEL, VertexId}
import io.github.scala_tessella.editor.utils.Geometry.Point
import io.github.scala_tessella.dcel.BigDecimalGeometry.AngleDegree
import io.github.scala_tessella.ring_seq.RingSeq.slidingO

import scala.math.*

/**
 * Shared geometry utilities to compute the exact placement (screen points) of a regular polygon
 * attached to a given edge. If intoFace is provided, the polygon is oriented towards that face's interior.
 */
object PolygonPlacementGeometry:

  /** Compute canvas coordinates of the wireframe points for a placement preview/failure. */
  def computeWireframePoints(angles: Vector[AngleDegree], edge: (VertexId, VertexId), tiling: TilingDCEL, intoFace: Option[FaceId] = None): Vector[(Double, Double)] =
    val vertex1 = tiling.coordinates(edge._1).toPoint
    val vertex2 = tiling.coordinates(edge._2).toPoint

    val (edgeLen, ux, uy, midPoint) = computeEdgeGeometrics(vertex1, vertex2)
    if (edgeLen == 0) return Vector.empty

    val (perpX, perpY, wasFlipped) = determineInwardNormal(tiling, edge, intoFace, (ux, uy))

    if angles.toSet.size == 1 then
      val polygonSides = angles.size
      val (apothem, radius, halfAngle) = computePolygonGeometrics(polygonSides, edgeLen)
  
      val center = Point(midPoint.x + perpX * apothem, midPoint.y + perpY * apothem)
  
      val angleStep = halfAngle * 2
      val edgeAngle = atan2(uy, ux)
      val startAngle = computeVertexStartAngle(edgeAngle, wasFlipped, polygonSides, halfAngle)
      val winding = if wasFlipped then -1 else 1
  
      generateWireframeVertices(polygonSides, center, radius, startAngle, angleStep, winding)
    else
      ???

  /** Calculates basic geometric properties of an edge. */
  private def computeEdgeGeometrics(vertex1: Point, vertex2: Point): (Double, Double, Double, Point) =
    val ex = vertex2.x - vertex1.x
    val ey = vertex2.y - vertex1.y
    val edgeLen = sqrt(ex * ex + ey * ey)
    val ux = if edgeLen == 0 then 0.0 else ex / edgeLen
    val uy = if edgeLen == 0 then 0.0 else ey / edgeLen
    val midPoint = Point((vertex1.x + vertex2.x) / 2, (vertex1.y + vertex2.y) / 2)
    (edgeLen, ux, uy, midPoint)

  /** Determines the inward-pointing normal vector for an edge relative to a face. */
  private def determineInwardNormal(
    tiling: TilingDCEL,
    edge: (VertexId, VertexId),
    intoFace: Option[FaceId],
    unitVector: (Double, Double)
  ): (Double, Double, Boolean) =
    val (ux, uy) = unitVector
    val leftNormal = (-uy, ux)  // Normal for CCW traversal
    val rightNormal = (uy, -ux) // Normal for CW traversal

    def findNormal(faceId: FaceId): Option[(Double, Double, Boolean)] =
      tiling.findInnerFace(faceId).toOption.flatMap { face =>
        face.getVertices.toOption.map(_.map(_.id).toVector).flatMap { ids =>
          if !ids.slidingO(2).exists(p => p(0) == edge._1 && p(1) == edge._2) then
            Some((leftNormal._1, leftNormal._2, false)) // Forward edge, use left normal, not flipped
          else if ids.slidingO(2).exists(p => p(0) == edge._2 && p(1) == edge._1) then
            Some((rightNormal._1, rightNormal._2, true)) // Backward edge, use right normal, flipped
          else
            None
        }
      }

    intoFace match
      case Some(fid) =>
        findNormal(fid).getOrElse((leftNormal._1, leftNormal._2, false))
      case None =>
        tiling.innerFaces.view
          .flatMap(f => findNormal(f.id))
          .headOption
          .getOrElse((leftNormal._1, leftNormal._2, false))

  /** Calculates geometric properties of the regular polygon to be placed. */
  private def computePolygonGeometrics(polygonSides: Int, sideLength: Double): (Double, Double, AngleDegree) =
    val halfAngle: AngleDegree = AngleDegree(180) / polygonSides
    val apothem = sideLength / (2 * tan(halfAngle.toBigRadian.toBigDecimal.toDouble))
    val radius = sideLength / (2 * sin(halfAngle.toBigRadian.toBigDecimal.toDouble))
    (apothem, radius, halfAngle)

  /** Calculates the starting angle for generating polygon vertices. */
  private def computeVertexStartAngle(edgeAngle: Double, wasFlipped: Boolean, polygonSides: Int, halfAngle: AngleDegree): Double =
    val isOdd = polygonSides % 2 == 1
    val baseOffset: AngleDegree = AngleDegree(90) - halfAngle
    val oddHalfStep = if isOdd && !wasFlipped then halfAngle else AngleDegree(0)
    edgeAngle + (baseOffset + oddHalfStep).toBigRadian.toBigDecimal.toDouble

  /** Generates the vertex points for the wireframe polygon. */
  private def generateWireframeVertices(
    polygonSides: Int,
    center: Point,
    radius: Double,
    startAngle: Double,
    angleStep: AngleDegree,
    winding: Int
  ): Vector[(Double, Double)] =
    (0 until polygonSides).map { i =>
      val a = startAngle + (angleStep * winding * i).toBigRadian.toBigDecimal.toDouble
      val px = center.x + radius * cos(a)
      val py = center.y + radius * sin(a)
      tilingPointToCanvasView(Point(px, py))
    }.toVector