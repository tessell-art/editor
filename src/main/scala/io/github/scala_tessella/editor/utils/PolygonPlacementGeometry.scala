package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.dcel.BigDecimalGeometry.AngleDegree
import io.github.scala_tessella.dcel.{FaceId, TilingDCEL, VertexId}
import io.github.scala_tessella.editor.operations.TessellationOperations.VertexCoord
import io.github.scala_tessella.editor.utils.Geometry.{Point, buildUnitEdgePolygon, edgeGeometrics}
import io.github.scala_tessella.editor.utils.TessellationGeometry.*
import io.github.scala_tessella.editor.utils.Radian
import io.github.scala_tessella.ring_seq.RingSeq.slidingO

import scala.math.*

/** Shared geometry utilities to compute the exact placement (screen points) of a regular polygon attached to
  * a given edge. If intoFace is provided, the polygon is oriented towards that face's interior.
  */
object PolygonPlacementGeometry:

  /** Compute canvas coordinates of the wireframe points for a placement preview/failure. */
  def computeWireframePoints(
      angles: Vector[AngleDegree],
      edge: (VertexCoord, VertexCoord),
      tiling: TilingDCEL,
      intoFace: Option[FaceId] = None
  ): Vector[Point] =
    val vertex1 = edge._1.point
    val vertex2 = edge._2.point

    val (edgeLen, unitVector, midPoint) = edgeGeometrics(vertex1, vertex2)
    if edgeLen == 0 then
      Vector.empty
    else
      val (perpX, perpY, wasFlipped) =
        determineInwardNormal(tiling, (edge._1.id, edge._2.id), intoFace, (unitVector.x, unitVector.y))

      if angles.toSet.size == 1 then
        val polygonSides                 = angles.size
        val (apothem, radius, halfAngle) = computePolygonGeometrics(polygonSides, edgeLen)

        val center = Point(midPoint.x + perpX * apothem, midPoint.y + perpY * apothem)

        val angleStep  = halfAngle * 2
        val edgeAngle  = Math.atan2(unitVector.y, unitVector.x)
        val startAngle = computeVertexStartAngle(edgeAngle, wasFlipped, polygonSides, halfAngle)
        val winding    = if wasFlipped then -1 else 1

        generateWireframeVertices(polygonSides, center, radius, startAngle, angleStep, winding)
      else

        // Irregular polygon with unit edges and given internal angles.
        val local = buildUnitEdgePolygon(angles.map(_.toBigRadian.toBigDecimal.toDouble).map(Radian(_))).init

        // Compute transform: align local first edge to the actual perimeter edge
        val edgeAngle = Radian(Math.atan2(unitVector.y, unitVector.x))

        // Rotate and scale each local point, then translate so that local (0,0) maps to vertex1
        val world = local.map { p =>
          val scaled  = p.scale(edgeLen)
          val rotated = scaled.rotate(edgeAngle)
          vertex1 + rotated
        }

        // No additional inward offset needed for preview points; keep exact constructed vertices
        world.map(tilingPointToCanvasView)

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
  ): Radian =
    val isOdd                   = polygonSides % 2 == 1
    val baseOffset: AngleDegree = AngleDegree(90) - halfAngle
    val oddHalfStep             = if isOdd && !wasFlipped then halfAngle else AngleDegree(0)
    Radian(edgeAngle + (baseOffset + oddHalfStep).toBigRadian.toBigDecimal.toDouble)

  /** Generates the vertex points for the wireframe polygon. */
  private def generateWireframeVertices(
      polygonSides: Int,
      center: Point,
      radius: Double,
      startAngle: Radian,
      angleStep: AngleDegree,
      winding: Int
  ): Vector[Point] =
    (0 until polygonSides).map { i =>
      val theta = startAngle + Radian((angleStep * winding * i).toBigRadian.toBigDecimal.toDouble)
      tilingPointToCanvasView(center.offsetPolar(radius, theta))
    }.toVector
