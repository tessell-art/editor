package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.dcel.BigDecimalGeometry.AngleDegree
import io.github.scala_tessella.dcel.{FaceId, TilingDCEL, VertexId}
import io.github.scala_tessella.editor.utils.Geometry.{Point2, Radian, edgeGeometrics}
import io.github.scala_tessella.editor.utils.TessellationGeometry.*
import io.github.scala_tessella.ring_seq.RingSeq.{rotateLeft, slidingO}

import scala.math.*

/** Shared geometry utilities to compute the exact placement (screen points) of a regular polygon attached to
  * a given edge. If intoFace is provided, the polygon is oriented towards that face's interior.
  */
object PolygonPlacementGeometry:

  /** Compute canvas coordinates of the wireframe points for a placement preview/failure. */
  def computeWireframePoints(
      angles: Vector[AngleDegree],
      edge: (VertexId, VertexId),
      tiling: TilingDCEL,
      intoFace: Option[FaceId] = None
  ): Vector[Point2] =
    val vertex1 = tiling.coordinates(edge._1).toPoint
    val vertex2 = tiling.coordinates(edge._2).toPoint

    val (edgeLen, unitVector, midPoint) = edgeGeometrics(vertex1, vertex2)
    if edgeLen == 0 then
      Vector.empty
    else
      val (perpX, perpY, wasFlipped) =
        determineInwardNormal(tiling, edge, intoFace, (unitVector.xx, unitVector.yy))

      if angles.toSet.size == 1 then
        val polygonSides                 = angles.size
        val (apothem, radius, halfAngle) = computePolygonGeometrics(polygonSides, edgeLen)

        val center = Point2(midPoint.xx + perpX * apothem, midPoint.yy + perpY * apothem)

        val angleStep  = halfAngle * 2
        val edgeAngle  = Math.atan2(unitVector.yy, unitVector.xx)
        val startAngle = computeVertexStartAngle(edgeAngle, wasFlipped, polygonSides, halfAngle)
        val winding    = if wasFlipped then -1 else 1

        generateWireframeVertices(polygonSides, center, radius, startAngle, angleStep, winding)
      else

        // Irregular polygon with unit edges and given internal angles.
        val local = buildUnitEdgePolygon(angles)

        // Compute transform: align local first edge to the actual perimeter edge
        val edgeAngle = Radian(Math.atan2(unitVector.yy, unitVector.xx))

        // Rotate and scale each local point, then translate so that local (0,0) maps to vertex1
        val world = local.map { p =>
          val scaled  = p.scale(edgeLen)
          val rotated = scaled.rotate(edgeAngle)
          vertex1 + rotated
        }

        // No additional inward offset needed for preview points; keep exact constructed vertices
        world.map(tilingPointToCanvasView)

  /** Build polygon vertices in local space using unit edge length and given internal angles. */
  private def buildUnitEdgePolygon(angles: Vector[AngleDegree]): Vector[Point2] =
    if angles.isEmpty then Vector.empty
    else
      var pts     = Vector.newBuilder[Point2]
      var heading = 0.0 // radians
      var curr    = Point2.origin

      // first vertex
      pts += curr

      // For each interior angle:
      // 1) advance one unit in current heading to create next vertex
      // 2) then turn by the exterior angle (PI - interior)
      angles.rotateLeft(1).foreach { a =>
        val nx   = curr.xx + cos(heading)
        val ny   = curr.yy + sin(heading)
        val next = Point2(nx, ny)
        pts += next
        curr = next
        heading = heading + (math.Pi - a.toBigRadian.toBigDecimal.toDouble)
      }

      // We now have N+1 points with the last equal to the first only for closed perfect polygons.
      // For preview we want exactly N vertices, so drop the last step-produced point.
      val built = pts.result()
      if built.size >= 2 then built.dropRight(1) else built

  /** Calculates basic geometric properties of an edge. */
  private def computeEdgeGeometrics(vertex1: Point2, vertex2: Point2): (Double, Double, Double, Point2) =
    val ex       = vertex2.xx - vertex1.xx
    val ey       = vertex2.yy - vertex1.yy
    val edgeLen  = sqrt(ex * ex + ey * ey)
    val ux       = if edgeLen == 0 then 0.0 else ex / edgeLen
    val uy       = if edgeLen == 0 then 0.0 else ey / edgeLen
    val midPoint = Point2((vertex1.xx + vertex2.xx) / 2, (vertex1.yy + vertex2.yy) / 2)
    (edgeLen, ux, uy, midPoint)

  /** Determines the inward-pointing normal vector for an edge relative to a face. */
  private def determineInwardNormal(
      tiling: TilingDCEL,
      edge: (VertexId, VertexId),
      intoFace: Option[FaceId],
      unitVector: (Double, Double)
  ): (Double, Double, Boolean) =
    val (ux, uy)    = unitVector
    val leftNormal  = (-uy, ux) // Normal for CCW traversal
    val rightNormal = (uy, -ux) // Normal for CW traversal

    def findNormal(faceId: FaceId): Option[(Double, Double, Boolean)] =
      tiling.findInnerFaceVertices(faceId).toOption.map(_.map(_.id).toVector).flatMap { ids =>

        if !ids.slidingO(2).exists(p => p(0) == edge._1 && p(1) == edge._2) then
          Some((leftNormal._1, leftNormal._2, false)) // Forward edge, use left normal, not flipped
        else if ids.slidingO(2).exists(p => p(0) == edge._2 && p(1) == edge._1) then
          Some((rightNormal._1, rightNormal._2, true)) // Backward edge, use right normal, flipped
        else
          None
      }

    intoFace match
      case Some(fid) =>
        findNormal(fid).getOrElse((leftNormal._1, leftNormal._2, false))
      case None      =>
        tiling.innerFaces.view
          .flatMap(f => findNormal(f.id))
          .headOption
          .getOrElse((leftNormal._1, leftNormal._2, false))

  /** Calculates geometric properties of the regular polygon to be placed. */
  private def computePolygonGeometrics(polygonSides: Int, sideLength: Double): (Double, Double, AngleDegree) =
    val halfAngle: AngleDegree = AngleDegree(180) / polygonSides
    val apothem                = sideLength / (2 * tan(halfAngle.toBigRadian.toBigDecimal.toDouble))
    val radius                 = sideLength / (2 * sin(halfAngle.toBigRadian.toBigDecimal.toDouble))
    (apothem, radius, halfAngle)

  /** Calculates the starting angle for generating polygon vertices. */
  private def computeVertexStartAngle(
      edgeAngle: Double,
      wasFlipped: Boolean,
      polygonSides: Int,
      halfAngle: AngleDegree
  ): Double =
    val isOdd                   = polygonSides % 2 == 1
    val baseOffset: AngleDegree = AngleDegree(90) - halfAngle
    val oddHalfStep             = if isOdd && !wasFlipped then halfAngle else AngleDegree(0)
    edgeAngle + (baseOffset + oddHalfStep).toBigRadian.toBigDecimal.toDouble

  /** Generates the vertex points for the wireframe polygon. */
  private def generateWireframeVertices(
      polygonSides: Int,
      center: Point2,
      radius: Double,
      startAngle: Double,
      angleStep: AngleDegree,
      winding: Int
  ): Vector[Point2] =
    (0 until polygonSides).map { i =>
      val a  = startAngle + (angleStep * winding * i).toBigRadian.toBigDecimal.toDouble
      val px = center.xx + radius * cos(a)
      val py = center.yy + radius * sin(a)
      tilingPointToCanvasView(Point2(px, py))
    }.toVector
