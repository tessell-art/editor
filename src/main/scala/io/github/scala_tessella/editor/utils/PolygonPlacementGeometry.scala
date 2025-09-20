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

      // Irregular polygon with unit edges and given internal angles.
      // Build a local polygon starting from origin along +X, then translate/rotate to align the first edge to (v1->v2) and offset inward.
      val local = buildUnitEdgePolygon(angles)
    
      // Compute transform: align local first edge (from (0,0) to (1,0)) to the actual perimeter edge (vertex1 -> vertex2)
      val edgeAngle = atan2(uy, ux)
      val rotCos = cos(edgeAngle)
      val rotSin = sin(edgeAngle)
    
      // Rotate and scale each local point, then translate so that local (0,0) maps to vertex1
      val world = local.map { p =>
        val sx = p.x * edgeLen
        val sy = p.y * edgeLen
        val rx = sx * rotCos - sy * rotSin
        val ry = sx * rotSin + sy * rotCos
        Point(vertex1.x + rx, vertex1.y + ry)
      }
    
      // Offset the polygon inward by moving its centroid along the inward normal by a small amount.
      // For a preview wireframe this helps visualize inward growth for irregular cases too.
      val (cx, cy) =
        if world.nonEmpty then
          val (sx, sy) = world.foldLeft((0.0, 0.0)) { case ((ax, ay), p) => (ax + p.x, ay + p.y) }
          (sx / world.length, sy / world.length)
        else (0.0, 0.0)
    
      // The offset magnitude: use a fraction of the edge length so preview remains close to boundary.
      val inwardOffset = edgeLen * 0.0 // keep vertices exactly on constructed positions; change to e.g. 0.0 or 0.05 to nudge inward
      val worldInward = world.map { p =>
        Point(p.x + perpX * inwardOffset, p.y + perpY * inwardOffset)
      }
    
      worldInward.map(tilingPointToCanvasView)

  /** Build polygon vertices in local space using unit edge length and given internal angles. */
  private def buildUnitEdgePolygon(angles: Vector[AngleDegree]): Vector[Point] =
    if angles.isEmpty then Vector.empty
    else
      // Start at origin, first edge along +X
      var pts = Vector(Point(0.0, 0.0))
      var heading = 0.0 // radians
      var curr = Point(0.0, 0.0)

      // Walk edges: after each edge move, turn by exterior angle (PI - interior)
      angles.foreach { a =>
        val rad = heading
        val nx = curr.x + cos(rad)
        val ny = curr.y + sin(rad)
        val next = Point(nx, ny)
        pts = pts :+ next
        heading = heading + (math.Pi - a.toBigRadian.toBigDecimal.toDouble)
      }

      // Close precision drift: translate so that the first edge midpoint aligns at the origin horizontally.
      // Not strictly necessary for placement, but keeps shape stable if used standalone.
      pts

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