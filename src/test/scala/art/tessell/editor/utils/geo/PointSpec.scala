package art.tessell.editor.utils.geo

import munit.FunSuite

class PointSpec extends FunSuite:

  private val eps                                                                                  = 1e-9
  private def assertAlmostEquals(a: Double, b: Double, tol: Double = eps)(clue: String = ""): Unit =
    assert(math.abs(a - b) <= tol, s"$clue expected=$b actual=$a diff=${math.abs(a - b)}")

  private def assertPointEquals(p: Point, x: Double, y: Double, tol: Double = eps, clue: String = ""): Unit =
    assertAlmostEquals(p.x, x, tol)(s"$clue x")
    assertAlmostEquals(p.y, y, tol)(s"$clue y")

  test("apply and accessors") {
    val p = Point(3.0, -2.5)
    assertAlmostEquals(p.x, 3.0)
    assertAlmostEquals(p.y, -2.5)
  }

  test("origin") {
    val o = Point.origin
    assertPointEquals(o, 0.0, 0.0)
  }

  test("fromPolar at axes") {
    // rho=1, theta=0 -> (1,0)
    val p0 = Point.fromPolar(1.0, Radian(0))
    assertPointEquals(p0, 1.0, 0.0)

    // rho=2, theta=PI/2 -> (0,2)
    val p90 = Point.fromPolar(2.0, Radian(Math.PI / 2))
    assertPointEquals(p90, 0.0, 2.0, tol = 1e-9)

    // rho=3, theta=PI -> (-3,0)
    val p180 = Point.fromPolar(3.0, Radian(Math.PI))
    assertPointEquals(p180, -3.0, 0.0, tol = 1e-9)
  }

  test("plus and +") {
    val a = Point(1.0, 2.0)
    val b = Point(3.5, -0.5)
    assertPointEquals(a.plus(b), 4.5, 1.5)
    assertPointEquals(a + b, 4.5, 1.5)
  }

  test("minus and -") {
    val a = Point(4.0, 1.0)
    val b = Point(3.0, 5.0)
    assertPointEquals(a.minus(b), 1.0, -4.0)
    assertPointEquals(a - b, 1.0, -4.0)
  }

  test("scale and scalar *") {
    val p = Point(2.0, -3.0)
    assertPointEquals(p.scale(0.5), 1.0, -1.5)
    assertPointEquals(p * 0.5, 1.0, -1.5)
  }

  test("Hadamard * (point times point)") {
    val a = Point(2.0, -3.0)
    val b = Point(4.0, 5.0)
    val c = a * b
    assertPointEquals(c, 8.0, -15.0)
  }

  test("scalar /") {
    val p = Point(5.0, -2.0)
    assertPointEquals(p / 2.0, 2.5, -1.0)
  }

  test("component-wise /") {
    val a = Point(8.0, -9.0)
    val b = Point(2.0, 3.0)
    val c = a / b
    assertPointEquals(c, 4.0, -3.0)
  }

  test("scaleAndTranslate") {
    val p   = Point(2.0, 3.0)
    val out = p.scaleAndTranslate(2.0, Point(-1.0, 4.0))
    // (2*2 -1, 3*2 +4) = (3, 10)
    assertPointEquals(out, 3.0, 10.0)
  }

  test("rotate by 0 and by PI/2 and by PI") {
    val p  = Point(1.0, 0.0)
    // 0
    val r0 = p.rotate(Radian(0))
    assertPointEquals(r0, 1.0, 0.0)

    // 90°
    val r90 = p.rotate(Radian(Math.PI / 2))
    assertPointEquals(r90, 0.0, 1.0, tol = 1e-9)

    // 180°
    val r180 = p.rotate(Radian(Math.PI))
    assertPointEquals(r180, -1.0, 0.0, tol = 1e-9)
  }

  test("rotateAround") {
    val p      = Point(2.0, 1.0)
    val origin = Point(1.0, 1.0)
    val r      = p.rotateAround(
      origin,
      Radian(Math.PI / 2)
    ) // translate to (1,0), rotate -> (0,1), translate back -> (1,2)
    assertPointEquals(r, 1.0, 2.0, tol = 1e-9)
  }

  test("offsetPolar") {
    val p = Point(1.0, 1.0)
    val q = p.offsetPolar(2.0, Radian(0))
    assertPointEquals(q, 3.0, 1.0)
  }

  test("angleTo using cardinal directions") {
    val a     = Point(1.0, 1.0)
    val east  = Point(3.0, 1.0)
    val north = Point(1.0, 4.0)
    val west  = Point(-2.0, 1.0)
    val south = Point(1.0, -2.0)

    assertAlmostEquals(a.angleTo(east).toDouble, 0.0)
    assertAlmostEquals(a.angleTo(north).toDouble, Math.PI / 2, tol = 1e-9)
    assertAlmostEquals(a.angleTo(west).toDouble, Math.PI, tol = 1e-9)
    assertAlmostEquals(a.angleTo(south).toDouble, -Math.PI / 2, tol = 1e-9)
  }

  test("distanceTo") {
    val a = Point(1.0, 2.0)
    val b = Point(4.0, 6.0)
    assertAlmostEquals(a.distanceTo(b), 5.0)
    assertAlmostEquals(b.distanceTo(a), 5.0)
  }

  test("alignWithStart (relative rotation)") {
    val first   = Point(0.0, 0.0)
    val second  = Point(1.0, 0.0) // angle 0 from first
    val p       = Point(0.0, 1.0) // vector from first at 90°
    // align p with start so that its direction rotates by TAU - angle(first->second) = TAU
    // rotating by full turn returns same vector translated to start-origin
    val aligned = p.alignWithStart(first, second)
    assertPointEquals(aligned, 0.0, 1.0, tol = 1e-9)
  }

  test("magnitude") {
    val p = Point(3.0, 4.0)
    assertAlmostEquals(p.magnitude, 5.0)
  }

  test("normalized non-zero") {
    val p = Point(3.0, 4.0).normalized
    assertAlmostEquals(p.magnitude, 1.0, tol = 1e-12)
    assertPointEquals(p, 0.6, 0.8, tol = 1e-12)
  }

  test("normalized near zero returns (0,0)") {
    val p = Point(1e-15, -1e-15).normalized
    assertPointEquals(p, 0.0, 0.0, tol = 0.0)
  }

  test("dot product") {
    val a = Point(1.0, 2.0)
    val b = Point(3.0, 4.0)
    assertAlmostEquals(a.dot(b), 11.0)
    assertAlmostEquals(b.dot(a), 11.0)
    assertAlmostEquals(a.dot(Point(-2.0, 1.0)), 0.0) // orthogonal to (1,2)
  }

  test("perp (90° CCW)") {
    val p = Point(2.0, 3.0)
    val q = p.perp
    assertPointEquals(q, -3.0, 2.0)
    // perp is orthogonal: dot = 0
    assertAlmostEquals(p.dot(q), 0.0)
  }

  test("projectOn axis") {
    val v    = Point(3.0, 4.0)   // magnitude 5
    val axis = Point(1.0, 0.0)   // x-axis
    val proj = v.projectOn(axis) // should be (3,0)
    assertPointEquals(proj, 3.0, 0.0, tol = 1e-12)

    val diag        = Point(1.0, 1.0)
    val proj2       = v.projectOn(diag) // projection length = dot(v, u) where u=(1,1)/sqrt(2)
    val u           = diag.normalized
    val expectedLen = v.dot(u)
    val expected    = u * expectedLen
    assertPointEquals(proj2, expected.x, expected.y, tol = 1e-12)
  }

  test("reflectAcross a line") {
    // Across the x-axis: y flips.
    assertPointEquals(Point(3.0, 2.0).reflectAcross(Point(0, 0), Point(1, 0)), 3.0, -2.0, tol = 1e-12)
    // Across the y-axis: x flips.
    assertPointEquals(Point(3.0, 2.0).reflectAcross(Point(0, 0), Point(0, 1)), -3.0, 2.0, tol = 1e-12)
    // Across the y = x diagonal: coordinates swap.
    assertPointEquals(Point(3.0, 2.0).reflectAcross(Point(0, 0), Point(1, 1)), 2.0, 3.0, tol = 1e-12)
    // A point on the line is its own reflection (line offset from the origin).
    assertPointEquals(Point(2.0, 5.0).reflectAcross(Point(2, 0), Point(2, 9)), 2.0, 5.0, tol = 1e-12)
    // Across the vertical line x = 2: mirror distance is preserved.
    assertPointEquals(Point(5.0, 1.0).reflectAcross(Point(2, 0), Point(2, 9)), -1.0, 1.0, tol = 1e-12)
  }
