package io.github.scala_tessella.editor.utils.geo

import io.github.scala_tessella.dcel.geometry.AngleDegree
import io.github.scala_tessella.editor.models.{EditorConfig, VertexCoord}
import io.github.scala_tessella.editor.operations.TessellationOperations.toCoords
import io.github.scala_tessella.editor.utils.TilingBuilders
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.*

class PolygonPlacementPropertySpec extends ScalaCheckSuite:

  private val eps = 1e-7

  // Square tiling — single face, 4 perimeter edges. Stable, cheap to reuse across properties.
  private val tiling = TilingBuilders.square

  // Boundary vertices in cyclic order, paired into 4 edges.
  private val perimeterEdges: Vector[(VertexCoord, VertexCoord)] =
    val coords = tiling.boundaryVertices.toOption.get.map(_.toCoords).toVector
    coords.indices.map(i => (coords(i), coords((i + 1) % coords.size))).toVector

  private val sidesGen: Gen[Int]     = Gen.chooseNum(3, 12)
  private val edgeIndexGen: Gen[Int] = Gen.chooseNum(0, perimeterEdges.size - 1)

  private def regularAngles(sides: Int): Vector[AngleDegree] =
    Vector.fill(sides)(AngleDegree((sides - 2) * 180.0 / sides))

  private def centroid(points: Vector[Point]): Point =
    Point(points.map(_.x).sum / points.size, points.map(_.y).sum / points.size)

  property("computeWireframePoints returns exactly `sides` vertices for a regular polygon"):
    forAll(sidesGen, edgeIndexGen): (sides, edgeIdx) =>

      val edge   = perimeterEdges(edgeIdx)
      val points = PolygonPlacementGeometry.computeWireframePoints(regularAngles(sides), edge, tiling)
      points.size == sides

  property("regular polygon vertices lie on a common circle around their centroid"):
    forAll(sidesGen, edgeIndexGen): (sides, edgeIdx) =>

      val edge   = perimeterEdges(edgeIdx)
      val points = PolygonPlacementGeometry.computeWireframePoints(regularAngles(sides), edge, tiling)
      val center = centroid(points)
      val radii  = points.map(p => center.distanceTo(p))
      val r0     = radii.head
      radii.forall(r => math.abs(r - r0) <= eps * math.max(1.0, r0))

  property("consecutive vertices of a regular polygon are equidistant (uniform side length)"):
    forAll(sidesGen, edgeIndexGen): (sides, edgeIdx) =>

      val edge     = perimeterEdges(edgeIdx)
      val points   = PolygonPlacementGeometry.computeWireframePoints(regularAngles(sides), edge, tiling)
      val sideLens = points.indices.map(i => points(i).distanceTo(points((i + 1) % points.size)))
      val s0       = sideLens.head
      sideLens.forall(s => math.abs(s - s0) <= eps * math.max(1.0, s0))

  property("regular polygon side length equals (tiling edge length × canvasScale) within ε"):
    forAll(sidesGen, edgeIndexGen): (sides, edgeIdx) =>

      val edge          = perimeterEdges(edgeIdx)
      val tilingEdgeLen = edge._1.point.distanceTo(edge._2.point)
      val expected      = tilingEdgeLen * EditorConfig.canvasScale

      val points  = PolygonPlacementGeometry.computeWireframePoints(regularAngles(sides), edge, tiling)
      val sideLen = points.head.distanceTo(points(1))
      math.abs(sideLen - expected) <= 1e-6 * math.max(1.0, expected)

  property("computeWireframePoints returns Vector.empty for a degenerate (zero-length) edge"):
    forAll(sidesGen): sides =>

      val source     = perimeterEdges.head._1
      val degenerate = (source, source) // same point twice → edgeLen == 0
      val points     = PolygonPlacementGeometry.computeWireframePoints(regularAngles(sides), degenerate, tiling)
      points.isEmpty
