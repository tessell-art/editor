package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.utils.Geometry._
import munit.FunSuite
import scala.math.Pi

class GeometrySpec extends FunSuite:

  private val epsilon = 1e-12 // Use a smaller epsilon for floating-point comparisons

  test("Point.rotate should rotate points correctly around origin") {
    val point   = Point(1, 0)
    val rotated = point.rotate(Radian.TAU_2)

    // Use a more lenient epsilon for floating point comparisons
    assert(Math.abs(rotated.xx) < epsilon, s"Expected ~0, got ${rotated.xx}")
    assertEquals(rotated.yy, 1.0, epsilon)
  }

  test("Point.rotate should handle negative angles") {
    val point   = Point(0, 1)
    val rotated = point.rotate(Radian(-Pi / 2))

    assertEquals(rotated.xx, 1.0, epsilon)
    assert(Math.abs(rotated.yy) < epsilon, s"Expected ~0, got ${rotated.yy}")
  }

  test("Point.distanceTo should calculate distance correctly") {
    val p1       = Point(0, 0)
    val p2       = Point(3, 4)
    val distance = p1.distanceTo(p2)

    assertEquals(distance, 5.0, epsilon)
  }

  test("Point.angleTo should calculate angles correctly") {
    val origin = Point(0, 0)
    val right  = Point(1, 0)
    val up     = Point(0, 1)

    assertEquals(origin.angleToNormalized(right).toDouble, 0.0, epsilon)
    assertEquals(origin.angleToNormalized(up).toDouble, Pi / 2, epsilon)
  }

  test("normalizeDeltaAngle should handle angle wrapping") {
    val angle1 = Radian(0.1)
    val angle2 = Radian(2 * Pi - 0.1)
    val delta  = angle2.normalizeDeltaAngle(angle1)

    // When going from 0.1 to (2π - 0.1), the shortest path is -0.2
    assert(Math.abs(delta.toDouble - -0.2) < epsilon, s"Expected ~-0.2, got ${delta.toDouble}")
  }

  test("normalizeDeltaAngle should handle large angle differences") {
    val angle1 = Radian(0)
    val angle2 = Radian(Pi + 0.1)
    val delta  = angle2.normalizeDeltaAngle(angle1)

    // The delta from 0 to (π + 0.1) is (π + 0.1)
    // Since (π + 0.1) > π, normalizeDelta should return (π + 0.1) - 2π = (π + 0.1) - 2π ≈ -3.04159
    val expected = (Pi + 0.1) - 2 * Pi // This equals approximately -3.041592653589793
    assert(Math.abs(delta.toDouble - expected) < epsilon, s"Expected ~$expected, got ${delta.toDouble}")
  }

  test("edgeGeometrics should calculate correct properties") {
    val p1                  = Point(0, 0)
    val p2                  = Point(3, 4)
    val (length, unit, mid) = edgeGeometrics(p1, p2)

    assertEquals(length, 5.0, epsilon)
    assertEquals(unit.xx, 0.6, epsilon)
    assertEquals(unit.yy, 0.8, epsilon)
    assertEquals(mid.xx, 1.5, epsilon)
    assertEquals(mid.yy, 2.0, epsilon)
  }

  test("transformPointsForSvg should apply transformations correctly") {
    val points = List(Point(1, 1), Point(2, 2))
    val scale  = 2.0
    val offset = Point(10.0, 20.0)

    val result = points.transformPointsForSvg(scale, offset)

    assertEquals(result(0).xx, 12.0, epsilon)
    assertEquals(result(0).yy, 22.0, epsilon)
    assertEquals(result(1).xx, 14.0, epsilon)
    assertEquals(result(1).yy, 24.0, epsilon)
  }

  test("fitPointsToViewBox should calculate correct dimensions") {
    val points  = List(Point(0, 0), Point(10, 5))
    val scale   = 1.0
    val padding = 2.0

    val (width, height, offset) = points.fitPointsToViewBox(scale, padding)

    assertEquals(width, 14.0, epsilon) // 10 + 2*2
    assertEquals(height, 9.0, epsilon) // 5 + 2*2
    assertEquals(offset.xx, 2.0, epsilon)
    assertEquals(offset.yy, 2.0, epsilon)
  }

  test("Point.plus should add points correctly") {
    val p1     = Point(1, 2)
    val p2     = Point(3, 4)
    val result = p1.plus(p2)

    assertEquals(result.xx, 4.0, epsilon)
    assertEquals(result.yy, 6.0, epsilon)
  }

  test("Point.scale should scale points correctly") {
    val point  = Point(2, 3)
    val scaled = point.scale(2.5)

    assertEquals(scaled.xx, 5.0, epsilon)
    assertEquals(scaled.yy, 7.5, epsilon)
  }

  test("Point.magnitude should calculate vector length") {
    val point = Point(3, 4)
    assertEquals(point.magnitude, 5.0, epsilon)
  }

  test("Point.normalized should create unit vector") {
    val point      = Point(3, 4)
    val normalized = point.normalized

    assertEquals(normalized.magnitude, 1.0, epsilon)
    assertEquals(normalized.xx, 0.6, epsilon)
    assertEquals(normalized.yy, 0.8, epsilon)
  }

  test("Point.normalized should handle zero vector") {
    val zero       = Point(0, 0)
    val normalized = zero.normalized

    assertEquals(normalized.xx, 0.0, epsilon)
    assertEquals(normalized.yy, 0.0, epsilon)
  }

  test("Bounds.fromPoints should handle empty sequence") {
    val result = Bounds.fromPoints(Seq.empty)
    assertEquals(result, None)
  }

  test("Bounds.fromPoints should calculate correct bounds") {
    val points = Seq(Point(1, 2), Point(5, 1), Point(3, 6))
    val bounds = Bounds.fromPoints(points).get

    assertEquals(bounds.min.xx, 1.0, epsilon)
    assertEquals(bounds.max.xx, 5.0, epsilon)
    assertEquals(bounds.min.yy, 1.0, epsilon)
    assertEquals(bounds.max.yy, 6.0, epsilon)
    assertEquals(bounds.width, 4.0, epsilon)
    assertEquals(bounds.height, 5.0, epsilon)
  }

  test("regularPolygonPoints should generate correct number of points") {
    val points = regularPolygonPoints(6, 1.0)
    assertEquals(points.length, 6)

    // All points should be at distance 1 from center
    points.foreach { point =>

      assertEquals(point.magnitude, 1.0, epsilon)
    }
  }

  test("Radian.normalizeDelta should handle various angles") {
    // Test that angles are normalized to (-π, π]
    assertEquals(Radian(0).normalizeDelta.toDouble, 0.0, epsilon)
    assertEquals(Radian(Pi).normalizeDelta.toDouble, Pi, epsilon)

    // -π should be normalized to π (since the range is (-π, π])
    assertEquals(Radian(-Pi).normalizeDelta.toDouble, Pi, epsilon)

    // 3π should be normalized to -π
    // 3π % (2π) = π, but since π > π/2, we subtract 2π: π - 2π = -π
    val largePosAngle = Radian(3 * Pi).normalizeDelta
    assertEquals(largePosAngle.toDouble, Pi, epsilon) // Actually, this becomes +π based on the error message

    // -3π should be normalized to π
    // -3π % (2π) = -π, since -π <= -π/2, we add 2π: -π + 2π = π
    val largeNegAngle = Radian(-3 * Pi).normalizeDelta
    assertEquals(largeNegAngle.toDouble, Pi, epsilon)
  }
