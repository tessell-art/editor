package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.dcel.{FaceId, TilingDCEL, VertexId}
import io.github.scala_tessella.dcel.BigDecimalGeometry.AngleDegree
import io.github.scala_tessella.dcel.Polygon.{RegularPolygon, SimplePolygon}
import munit.FunSuite

class PolygonPlacementGeometrySpec extends FunSuite:

  test("computeWireframePoints for regular triangle yields 3 points") {
    val tiling = TilingDCEL.createRegularPolygon(RegularPolygon(3))
    val angles = Vector.fill(3)(AngleDegree(60))
    val pts    = PolygonPlacementGeometry.computeWireframePoints(
      angles,
      (VertexId("V1"), VertexId("V2")),
      tiling,
      Some(FaceId("F1"))
    )
    assert(pts.size == 3)
    // Should not be collinear with the edge (has non-zero Y for at least one)
    assert(pts.exists(_.y != 0.0))
  }

  test("computeWireframePoints handles irregular polygon angles size N producing N points") {
    val angles = Vector(AngleDegree(60), AngleDegree(120), AngleDegree(60), AngleDegree(120))
    val tiling = TilingDCEL.createSimplePolygon(SimplePolygon(angles)).toOption.get
    val pts    = PolygonPlacementGeometry.computeWireframePoints(
      angles,
      (VertexId("V1"), VertexId("V2")),
      tiling,
      Some(FaceId("F1"))
    )
    assert(pts.size == angles.size)
  }
