package io.github.scala_tessella.editor.utils

import munit.FunSuite

class RadianSpec extends FunSuite {

  private val eps = 1e-12

  private def ≈(a: Double, b: Double, tol: Double = eps): Boolean =
    Math.abs(a - b) <= tol

  test("apply and toDouble should round-trip") {
    val d = 1.23456789
    val r = Radian(d)
    assertEqualsDouble(r.toDouble, d, eps)
  }

  test("fromDegrees/toDegrees should convert correctly") {
    val r90  = Radian.fromDegrees(90)
    val r180 = Radian.fromDegrees(180)
    assert(≈(r90.toDouble, Math.PI / 2))
    assert(≈(r180.toDouble, Math.PI))
    assertEqualsDouble(Radian(Math.PI).toDegrees, 180.0, eps)
    assertEqualsDouble(Radian(Math.PI / 2).toDegrees, 90.0, eps)
  }

  test("constants TAU_2 and TAU should be consistent with Math.PI") {
    assert(≈(Radian.TAU_2.toDouble, Math.PI))
    // TAU is defined via TAU * 2 in code, but should equal 2 * PI at runtime
    assert(≈(Radian.TAU.toDouble, 2 * Math.PI))
  }

  // normalize: result in [0, TAU)
  test("normalize should keep values already in range [0, TAU)") {
    val r0   = Radian(0.0)
    val r1   = Radian(Math.PI / 3)
    val rTau = Radian(2 * Math.PI - 1e-9)
    assertEqualsDouble(r0.normalize.toDouble, 0.0, eps)
    assertEqualsDouble(r1.normalize.toDouble, r1.toDouble, eps)
    assertEqualsDouble(rTau.normalize.toDouble, rTau.toDouble, eps)
  }

  test("normalize should wrap negatives into [0, TAU)") {
    val rNegSmall = Radian(-0.1)
    val rNegLarge = Radian(-10 * Math.PI)
    val n1        = rNegSmall.normalize.toDouble
    val n2        = rNegLarge.normalize.toDouble
    assert(n1 >= 0 && n1 < 2 * Math.PI)
    assert(n2 >= 0 && n2 < 2 * Math.PI)
    assert(≈(Radian(-2 * Math.PI).normalize.toDouble, 0.0))
    assert(≈(Radian(-2 * Math.PI - 1e-9).normalize.toDouble, 2 * Math.PI - 1e-9))
  }

  test("normalize should wrap values >= TAU back into [0, TAU)") {
    val r360 = Radian(2 * Math.PI)
    val r450 = Radian(2 * Math.PI + Math.PI / 2)
    assertEqualsDouble(r360.normalize.toDouble, 0.0, eps)
    assertEqualsDouble(r450.normalize.toDouble, Math.PI / 2, eps)
  }

  // normalizeDelta: result in (-PI, PI]
  test("normalizeDelta should keep values already in (-PI, PI]") {
    val rSmallPos = Radian(0.1)
    val rSmallNeg = Radian(-0.1)
    val rEdgePi   = Radian(Math.PI)
    assertEqualsDouble(rSmallPos.normalizeDelta.toDouble, 0.1, eps)
    assertEqualsDouble(rSmallNeg.normalizeDelta.toDouble, -0.1, eps)
    assertEqualsDouble(rEdgePi.normalizeDelta.toDouble, Math.PI, eps)
  }

  test("normalizeDelta should wrap to (-PI, PI] for large magnitudes") {
    // 3*PI/2 == -PI/2 in (-PI, PI]
    val r3pi2 = Radian(1.5 * Math.PI)
    assertEqualsDouble(r3pi2.normalizeDelta.toDouble, -Math.PI / 2, eps)

    val rMinus3pi2 = Radian(-1.5 * Math.PI)
    assertEqualsDouble(rMinus3pi2.normalizeDelta.toDouble, Math.PI / 2, eps)

    // multiples of TAU normalize to 0
    assertEqualsDouble(Radian(2 * Math.PI).normalizeDelta.toDouble, 0.0, eps)
    assertEqualsDouble(Radian(-2 * Math.PI).normalizeDelta.toDouble, 0.0, eps)

    // just over PI should wrap to negative small
    val tiny       = 1e-9
    val justOverPi = Radian(Math.PI + tiny).normalizeDelta.toDouble
    assert(≈(justOverPi, -(Math.PI - tiny)))
  }

  test("normalizeDeltaAngle should compute minimal signed difference") {
    val a = Radian(Math.PI / 6)      // 30 deg
    val b = Radian(11 * Math.PI / 6) // 330 deg
    // difference should be +60 deg (PI/3)
    assertEqualsDouble(a.normalizeDeltaAngle(b).toDouble, Math.PI / 3, eps)

    // opposite direction should be -PI/3
    assertEqualsDouble(b.normalizeDeltaAngle(a).toDouble, -Math.PI / 3, eps)

    // equal angles -> 0
    assertEqualsDouble(a.normalizeDeltaAngle(a).toDouble, 0.0, eps)
  }

  test("arithmetic operators + and - between radians") {
    val a = Radian(Math.PI / 4)
    val b = Radian(Math.PI / 6)
    assertEqualsDouble((a + b).toDouble, Math.PI / 4 + Math.PI / 6, eps)
    assertEqualsDouble((a - b).toDouble, Math.PI / 4 - Math.PI / 6, eps)
  }

  test("arithmetic operators * and / by Int") {
    val a = Radian(Math.PI / 12) // 15 deg
    assertEqualsDouble((a * 3).toDouble, 3 * Math.PI / 12, eps)
    assertEqualsDouble((a / 3).toDouble, Math.PI / 36, eps)
  }

  // Helper because munit's assertEquals for Double needs delta
  private def assertEqualsDouble(obtained: Double, expected: Double, delta: Double): Unit =
    assert(
      Math.abs(obtained - expected) <= delta,
      s"obtained=$obtained expected=$expected delta=$delta"
    )
}
