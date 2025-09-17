package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.utils.TessellationGeometry.*
import io.github.scala_tessella.dcel.{FaceId, TilingDCEL, VertexId}
import io.github.scala_tessella.dcel.BigDecimalGeometry.BigPoint
import io.github.scala_tessella.tessella.Geometry.Point
import io.github.scala_tessella.tessella.BigDecimalGeometry.{AngleDegree, BigCoords}
import io.github.scala_tessella.ring_seq.RingSeq.slidingO

import scala.math.*

/**
 * Shared geometry utilities to compute the exact placement (screen points) of a regular polygon
 * attached to a given edge. If intoFace is provided, the polygon is oriented towards that face's interior.
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
    val leftNormal  = (-uy,  ux)  // interior for CCW faces
    val rightNormal = ( uy, -ux)  // interior for CW faces

    // Midpoint of the edge
    val midX = (vertex1.x + vertex2.x) / 2
    val midY = (vertex1.y + vertex2.y) / 2

    // Determine inward normal using DCEL face boundary orientation (CCW for inner faces).
    // If the edge appears as (v2 -> v1) on the face boundary, interior is leftNormal.
    // If the edge appears as (v1 -> v2), interior (for that directed edge) is left of (v1 -> v2),
    // which corresponds to rightNormal relative to (v2 -> v1).
    def normalForFace(faceId: FaceId): Option[(Double, Double, Boolean)] =
      val face = tiling.findInnerFace(faceId).toOption.get
      val ids: Vector[VertexId] = face.getVertices.toOption.get.map(_.id).toVector
      val forward = ids.slidingO(2).exists(p => p(0) == edge._1 && p(1) == edge._2)
      if !forward then Some((leftNormal._1, leftNormal._2, false))
      else
        val backward = ids.slidingO(2).exists(p => p(0) == edge._2 && p(1) == edge._1)
        if backward then Some((rightNormal._1, rightNormal._2, true)) else None

    val (perpX, perpY, wasFlipped) = intoFace match
      case Some(fid) =>
        normalForFace(fid).getOrElse((leftNormal._1, leftNormal._2, false))
      case None =>
        // Try to identify any inner face containing the directed edge (either orientation)
        val found =
          tiling.innerFaces.view.flatMap(f => normalForFace(f.id)).headOption
        found.getOrElse((leftNormal._1, leftNormal._2, false))

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
