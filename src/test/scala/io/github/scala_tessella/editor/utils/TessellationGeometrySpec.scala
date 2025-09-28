package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.utils.Geometry.{Bounds, Point, maybeBounds}
import munit.FunSuite

class TessellationGeometrySpec extends FunSuite {

  test("maybeBounds should return None for an empty sequence of points") {
    val points = Seq.empty[Point]
    assertEquals(points.maybeBounds, None)
  }

  test("maybeBounds should calculate correct bounds for a sequence of points") {
    val points         = Seq(Point(1.0, 2.0), Point(-1.0, 5.0), Point(3.0, 0.0))
    val expectedBounds = Some(Bounds(min = Point(-1.0, 0.0), max = Point(3.0, 5.0)))
    assertEquals(points.maybeBounds, expectedBounds)
  }

  test("maybeBounds should work for a single point") {
    val points         = Seq(Point(10.0, 20.0))
    val expectedBounds = Some(Bounds(min = Point(10.0, 20.0), max = Point(10.0, 20.0)))
    assertEquals(points.maybeBounds, expectedBounds)
  }

  test("maybeBounds should work for points on an axis") {
    val points         = Seq(Point(0.0, 1.0), Point(0.0, 5.0), Point(0.0, -2.0))
    val expectedBounds = Some(Bounds(min = Point(0.0, -2.0), max = Point(0.0, 5.0)))
    assertEquals(points.maybeBounds, expectedBounds)
  }

}
