package io.github.scala_tessella.editor.utils.geo

import munit.ScalaCheckSuite
import org.scalacheck.Gen
import org.scalacheck.Prop.*

class RadianPropertySpec extends ScalaCheckSuite:

  private val eps = 1e-9

  // Wide range so wrapping behaviour is exercised, but bounded to keep ε meaningful.
  private val angleGen: Gen[Radian] =
    Gen.chooseNum(-100.0 * Math.PI, 100.0 * Math.PI).map(Radian(_))

  private val degreesGen: Gen[Double] =
    Gen.chooseNum(-3600.0, 3600.0)

  property("normalize maps any angle into [0, TAU)"):
    forAll(angleGen): r =>

      val n = r.normalize.toDouble
      n >= 0.0 && n < Radian.TAU.toDouble

  property("normalize is idempotent"):
    forAll(angleGen): r =>

      val once  = r.normalize
      val twice = once.normalize
      math.abs(twice.toDouble - once.toDouble) <= eps

  property("normalize is invariant under shifts of TAU"):
    val kGen = Gen.chooseNum(-10, 10)
    forAll(angleGen, kGen): (r, k) =>

      // Compare modulo TAU: when r is near a multiple of TAU, FP rounding can push the two
      // normalized values onto opposite sides of the [0, TAU) boundary. normalizeDeltaAngle
      // wraps that gap into (-π, π], which is the correct notion of "equal angles".
      val shifted = (r + Radian(k * Radian.TAU.toDouble)).normalize
      math.abs(shifted.normalizeDeltaAngle(r.normalize).toDouble) <= 1e-7

  property("normalizeDelta maps any angle into (-PI, PI]"):
    forAll(angleGen): r =>

      val d = r.normalizeDelta.toDouble
      d > -Radian.TAU_2.toDouble - eps && d <= Radian.TAU_2.toDouble + eps

  property("addition is commutative modulo TAU"):
    forAll(angleGen, angleGen): (a, b) =>

      val ab = (a + b).normalize.toDouble
      val ba = (b + a).normalize.toDouble
      math.abs(ab - ba) <= eps

  property("Math.toDegrees ∘ fromDegrees is identity within ε"):
    forAll(degreesGen): d =>

      val r       = Radian.fromDegrees(d)
      val backDeg = r.toDegrees
      math.abs(backDeg - d) <= 1e-9 * math.max(1.0, math.abs(d))

  property("normalizeDeltaAngle returns the smallest signed rotation (|delta| <= PI)"):
    forAll(angleGen, angleGen): (a, b) =>

      val delta = a.normalizeDeltaAngle(b).toDouble
      math.abs(delta) <= Radian.TAU_2.toDouble + eps

  property("(r * n) / n == r for non-zero n"):
    val nonZeroIntGen = Gen.oneOf(Gen.chooseNum(1, 100), Gen.chooseNum(-100, -1))
    forAll(angleGen, nonZeroIntGen): (r, n) =>

      val roundTrip = (r * n) / n
      math.abs(roundTrip.toDouble - r.toDouble) <= 1e-9 * math.max(1.0, math.abs(r.toDouble))
