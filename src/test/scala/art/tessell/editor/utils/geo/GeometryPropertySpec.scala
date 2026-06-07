package art.tessell.editor.utils.geo

import Geometry.*
import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.*

class GeometryPropertySpec extends ScalaCheckSuite:

  private val eps = 1e-9

  private val doubleGen: Gen[Double] =
    Gen.chooseNum(-1000.0, 1000.0)

  private val positiveDoubleGen: Gen[Double] =
    Gen.chooseNum(0.1, 1000.0)

  private val angleGen: Gen[Radian] =
    Gen.chooseNum(-4.0 * Math.PI, 4.0 * Math.PI).map(Radian(_))

  private val pointGen: Gen[Point] =
    for
      x <- doubleGen
      y <- doubleGen
    yield Point(x, y)

  property("rotate preserves distance from origin"):
    forAll(pointGen, angleGen): (p, angle) =>

      val rotated = p.rotate(angle)
      val d1      = Point.origin.distanceTo(p)
      val d2      = Point.origin.distanceTo(rotated)
      math.abs(d1 - d2) <= eps

  property("normalized unit vectors have magnitude 1 for non-zero points"):
    forAll(pointGen): p =>

      val mag = p.magnitude
      if mag > 1e-6 then
        math.abs(p.normalized.magnitude - 1.0) <= 1e-9
      else
        p.normalized == Point.origin

  property("regularPolygonPoints returns points at the given radius"):
    val sidesGen  = Gen.chooseNum(3, 20)
    val centerGen = pointGen
    forAll(sidesGen, positiveDoubleGen, centerGen): (sides, radius, center) =>

      val pts = regularPolygonPoints(sides, radius, center)
      (pts.size == sides) && pts.forall: p =>
        math.abs(center.distanceTo(p) - radius) <= 1e-7

  property("fitPointsToViewBox keeps transformed points within padded bounds"):
    val pointsGen = Gen.nonEmptyListOf(pointGen).map(_.toVector)
    val scaleGen  = Gen.chooseNum(0.1, 10.0)
    val padGen    = Gen.chooseNum(0.0, 50.0)

    forAll(pointsGen, scaleGen, padGen): (pts, scale, pad) =>

      val (w, h, off) = pts.fitPointsToViewBox(scale, pad)
      pts.forall: p =>

        val t = p.scaleAndTranslate(scale, off)
        t.x >= pad - eps && t.x <= w - pad + eps &&
        t.y >= pad - eps && t.y <= h - pad + eps

  property("buildUnitEdgePolygon produces unit-length steps"):
    val anglesGen = Gen.chooseNum(1, 20).flatMap(n => Gen.listOfN(n, angleGen))
    forAll(anglesGen): angles =>

      val pts = buildUnitEdgePolygon(angles)
      pts.sliding(2).forall:
        case Seq(a, b) => math.abs(a.distanceTo(b) - 1.0) <= 1e-7
        case _         => true
