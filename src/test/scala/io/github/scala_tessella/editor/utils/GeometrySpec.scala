package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.utils.Geometry.*
import munit.FunSuite
import scala.math.Pi

class GeometrySpec extends FunSuite:

  private val eps = 1e-12

  test("Bounds.fromPoints should return None for empty input") {
    assertEquals(Bounds.fromPoints(Seq.empty), None)
  }

  test("Bounds.fromPoints should compute min/max/width/height") {
    val pts    = Seq(Point(1, 2), Point(5, 1), Point(3, 6))
    val bounds = Bounds.fromPoints(pts).get
    assertEqualsDouble(bounds.min.x, 1.0, eps)
    assertEqualsDouble(bounds.min.y, 1.0, eps)
    assertEqualsDouble(bounds.max.x, 5.0, eps)
    assertEqualsDouble(bounds.max.y, 6.0, eps)
    assertEqualsDouble(bounds.width, 4.0, eps)
    assertEqualsDouble(bounds.height, 5.0, eps)
  }

  test("Bounds diagonal and center should be correct") {
    val b = Bounds(Point(0, 0), Point(4, 2))
    val d = b.diagonal
    assertEqualsDouble(d.length, Math.hypot(4.0, 2.0), eps)
    val c = b.center
    assertEqualsDouble(c.x, 2.0, eps)
    assertEqualsDouble(c.y, 1.0, eps)
  }

  test("maybeBounds should delegate to Bounds.fromPoints") {
    val pts = Seq(Point(0, 0), Point(2, 2))
    val mb  = pts.maybeBounds
    assert(mb.isDefined)
    val b   = mb.get
    assertEqualsDouble(b.min.x, 0.0, eps)
    assertEqualsDouble(b.max.y, 2.0, eps)
  }

  test("transformPointsForSvg applies scale then translation") {
    val pts    = List(Point(1, 1), Point(2, 2))
    val scale  = 2.0
    val offset = Point(10.0, 20.0)
    val res    = pts.transformPointsForSvg(scale, offset)
    assertEqualsDouble(res(0).x, 12.0, eps)
    assertEqualsDouble(res(0).y, 22.0, eps)
    assertEqualsDouble(res(1).x, 14.0, eps)
    assertEqualsDouble(res(1).y, 24.0, eps)
  }

  test("fitPointsToViewBox computes width/height/offset") {
    val pts         = List(Point(0, 0), Point(10, 5))
    val (w, h, off) = pts.fitPointsToViewBox(scale = 1.0, padding = 2.0)
    assertEqualsDouble(w, 14.0, eps) // 10 + 2*2
    assertEqualsDouble(h, 9.0, eps)  // 5 + 2*2
    assertEqualsDouble(off.x, 2.0, eps)
    assertEqualsDouble(off.y, 2.0, eps)
  }

  test("fitPointsToViewBox on empty points returns padding-only box") {
    val (w, h, off) = Seq.empty[Point].fitPointsToViewBox(scale = 3.0, padding = 5.0)
    assertEqualsDouble(w, 10.0, eps)
    assertEqualsDouble(h, 10.0, eps)
    assertEqualsDouble(off.x, 5.0, eps)
    assertEqualsDouble(off.y, 5.0, eps)
  }

  test("fitPointsToSquare scales by max dimension and centers") {
    val pts          = Seq(Point(0, 0), Point(10, 5))
    val size         = 100.0
    val pad          = 10.0
    val (scale, off) = pts.fitPointsToSquare(size, pad)
    // Extents: width=10, height=5 -> scale by (size-2*pad)/max = 80/10 = 8
    assertEqualsDouble(scale, 8.0, eps)
    // After scaling, drawing size is 80x40; centered => leftover (100-80)=20 => 10 each side on X
    // Y leftover (100-40)=60 => 30 each side. Offset includes -scale*min + centering
    assertEqualsDouble(off.x, 10.0, eps)
    assertEqualsDouble(off.y, 30.0, eps)
  }

  test("fitPointsToSquare with degenerate width or height uses epsilon floor") {
    val ptsVertical = Seq(Point(2, 0), Point(2, 10))
    val size        = 50.0
    val pad         = 5.0
    val (scaleV, _) = ptsVertical.fitPointsToSquare(size, pad)
    // width≈0 -> width clamped to 1e-6; height=10 -> scale should be (size-2*pad)/max = 40/10 = 4
    assertEqualsDouble(scaleV, 4.0, eps)

    val ptsHorizontal = Seq(Point(0, 3), Point(10, 3))
    val (scaleH, _)   = ptsHorizontal.fitPointsToSquare(size, pad)
    assertEqualsDouble(scaleH, 4.0, eps)
  }

  test("regularPolygonPoints produces correct count and radius") {
    val sides = 6
    val r     = 2.5
    val pts   = regularPolygonPoints(sides, r)
    assertEquals(pts.size, sides)
    // All points should lie at radius r from center
    pts.foreach { p =>
      val dist = Point(0, 0).distanceTo(p)
      assertEqualsDouble(dist, r, 1e-9)
    }
  }

  test("regularPolygonPoints centered at custom center") {
    val c   = Point(10, -3)
    val r   = 1.0
    val pts = regularPolygonPoints(4, r, c)
    pts.foreach { p =>

      assertEqualsDouble(c.distanceTo(p), r, 1e-9)
    }
  }

  test("buildUnitEdgePolygon creates a unit-step walk with given turns") {
    // Square: four unit steps turning by +90° (Pi/2). Starts at (0,0), heading +x.
    val turns    = Seq.fill(4)(Radian(Pi / 2))
    val path     = buildUnitEdgePolygon(turns)
    assertEquals(path.head, Point(0, 0))
    // Expected vertices: (0,0) -> (1,0) -> (1,1) -> (0,1) -> (0,0)
    val expected = Vector(Point(0, 0), Point(1, 0), Point(1, 1), Point(0, 1), Point(0, 0))
    assertEquals(path.size, expected.size)
    expected.zip(path).foreach { case (e, p) =>
      assertEqualsDouble(e.x, p.x, 1e-9)
      assertEqualsDouble(e.y, p.y, 1e-9)
    }
  }

  test("edgeMetrics returns length, unit vector (v2 - v1), and midpoint") {
    val p1               = Point(0, 0)
    val p2               = Point(3, 4)
    val (len, unit, mid) = edgeMetrics(p1, p2)
    assertEqualsDouble(len, 5.0, eps)
    assertEqualsDouble(unit.x, 0.6, eps)
    assertEqualsDouble(unit.y, 0.8, eps)
    assertEqualsDouble(mid.x, 1.5, eps)
    assertEqualsDouble(mid.y, 2.0, eps)
  }

  test("regularPolygonMetrics returns apothem, radius, and halfAngle") {
    val sides                        = 6
    val s                            = 1.0
    val (apothem, radius, halfAngle) = regularPolygonMetrics(sides, s)
    val expectedHalf                 = Radian.TAU_2 / sides
    assertEqualsDouble(halfAngle.toDouble, expectedHalf.toDouble, eps)
    // Check relationships: side = 2 R sin(halfAngle), apothem = R cos(halfAngle)
    val RfromSide                    = s / (2 * Math.sin(expectedHalf.toDouble))
    assertEqualsDouble(radius, RfromSide, 1e-12)
    val apFromR                      = RfromSide * Math.cos(expectedHalf.toDouble)
    assertEqualsDouble(apothem, apFromR, 1e-12)
  }

  // Helpers
  private def assertEqualsDouble(actual: Double, expected: Double, delta: Double): Unit =
    assert(math.abs(actual - expected) <= delta, clues(s"expected=$expected actual=$actual delta=$delta"))
