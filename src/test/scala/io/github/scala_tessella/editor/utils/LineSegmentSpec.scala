package io.github.scala_tessella.editor.utils

import munit.FunSuite

class LineSegmentSpec extends FunSuite:

  test("apply constructs a segment and accessors p1/p2 expose endpoints") {
    val a   = Point(0, 0)
    val b   = Point(3, 4)
    val seg = LineSegment(a, b)
    assertEquals(seg.p1, a)
    assertEquals(seg.p2, b)
  }

  test("dx and dy compute component differences") {
    val seg1 = LineSegment(Point(1, 2), Point(4, 6))
    assertEqualsDouble(seg1.dx, 3.0, 1e-12)
    assertEqualsDouble(seg1.dy, 4.0, 1e-12)

    val seg2 = LineSegment(Point(5.5, -2.2), Point(1.5, 3.8))
    assertEqualsDouble(seg2.dx, -4.0, 1e-12)
    assertEqualsDouble(seg2.dy, 6.0, 1e-12)
  }

  test("midPoint is the average of endpoints") {
    val seg = LineSegment(Point(0, 0), Point(2, 2))
    assertEquals(seg.midPoint, Point(1, 1))

    val seg2 = LineSegment(Point(-2, 5), Point(4, 1))
    val mid  = seg2.midPoint
    assertEqualsDouble(mid.x, 1.0, 1e-12)
    assertEqualsDouble(mid.y, 3.0, 1e-12)
  }

  test("length computes Euclidean distance") {
    val seg = LineSegment(Point(0, 0), Point(3, 4))
    assertEqualsDouble(seg.length, 5.0, 1e-12)

    val segZero = LineSegment(Point(1.2, -3.4), Point(1.2, -3.4))
    assertEqualsDouble(segZero.length, 0.0, 1e-12)
  }

  test("unitVector returns a normalized direction or origin for zero-length") {
    val seg = LineSegment(Point(0, 0), Point(3, 4))
    val u   = seg.unitVector
    // direction should be (3/5, 4/5)
    assertEqualsDouble(u.x, 0.6, 1e-12)
    assertEqualsDouble(u.y, 0.8, 1e-12)

    val segZero = LineSegment(Point(2, 2), Point(2, 2))
    val u0      = segZero.unitVector
    assertEquals(u0, Point.origin)
  }

  test("horizontalAngle is atan2(dy, dx) in radians") {
    // 0 degrees (pointing right)
    val s0 = LineSegment(Point(0, 0), Point(2, 0))
    assertEqualsDouble(s0.horizontalAngle.toDouble, 0.0, 1e-12)

    // 90 degrees (pointing up)
    val s90 = LineSegment(Point(0, 0), Point(0, 1))
    assertEqualsDouble(s90.horizontalAngle.toDouble, Math.PI / 2, 1e-12)

    // 180 or -180 degrees (pointing left)
    val s180 = LineSegment(Point(0, 0), Point(-1, 0))
    // angle should be pi or -pi; compare cosine to avoid sign ambiguity
    assertEqualsDouble(Math.cos(s180.horizontalAngle.toDouble), -1.0, 1e-12)
    assertEqualsDouble(Math.sin(s180.horizontalAngle.toDouble), 0.0, 1e-12)

    // 225 degrees (-135) (dx=-1, dy=-1)
    val s225 = LineSegment(Point(0, 0), Point(-1, -1))
    val ang  = s225.horizontalAngle.toDouble
    assert(ang <= 0)                              // in [-pi, pi]
    assertEqualsDouble(Math.tan(ang), 1.0, 1e-12) // atan2(-1,-1) has tan = 1
  }

  // helpers
  private def assertEqualsDouble(obtained: Double, expected: Double, eps: Double): Unit =
    assert(math.abs(obtained - expected) <= eps, clues(s"obtained=$obtained expected=$expected eps=$eps"))
