package io.github.scala_tessella.editor.utils.geo

import scala.annotation.targetName

/** Standard unit of angular measure */
opaque type Radian = Double

/** Companion object for [[Radian]] */
object Radian:

  /** Create a [[Radian]] from a `Double` */
  inline def apply(d: Double): Radian =
    d

  def fromDegrees(degrees: Double): Radian =
    Math.toRadians(degrees)

  /** @see
    *   [[https://tauday.com/]]
    */
  val TAU_2: Radian = Radian(Math.PI)
  val TAU: Radian   = TAU_2 * 2

  extension (r: Radian)

    /** @return the underlying `Double` */
    inline def toDouble: Double =
      r

    def toDegrees: Double =
      Math.toDegrees(r)

    /** Normalize any absolute angle to the half-open interval [0, TAU).
      *
      * Semantics:
      *   - The result is always greater than or equal to 0.0 and strictly less than TAU.
      * @example
      *   - 0.normalize == 0
      *   - (2π).normalize == 0
      *   - (3π).normalize == π
      *   - (-π/2).normalize == 3π/2
      *   - (-2π).normalize == 0
      */
    def normalize: Radian = (r % Radian.TAU + Radian.TAU) % Radian.TAU

    /** Normalize a signed angular difference to the interval (-PI, PI].
      *
      * Intended for "delta" angles (e.g., target - current), producing the smallest signed rotation.
      *
      * Semantics:
      *   - The result is strictly greater than -PI and less than or equal to PI.
      *   - Positive results indicate counterclockwise rotation; negative results indicate clockwise rotation.
      * @example
      *   - (π/2).normalizeDelta == π/2
      *   - (3π).normalizeDelta == -π
      *   - (-3π/2).normalizeDelta == π/2
      *   - (0).normalizeDelta == 0
      */
    def normalizeDelta: Radian =
      val d = r % Radian.TAU
      if d <= -Radian.TAU_2 then
        d + Radian.TAU
      else if d > Radian.TAU_2 then
        d - Radian.TAU
      else
        d

    /** Normalize delta angle to (-PI, PI]. */
    def normalizeDeltaAngle(other: Radian): Radian = (r - other).normalizeDelta

    @targetName("plus")
    def +(that: Radian): Radian =
      r + that

    @targetName("minus")
    def -(that: Radian): Radian =
      r - that

    @targetName("times")
    def *(i: Int): Radian =
      r * Radian(i)

    @targetName("divide")
    def /(i: Int): Radian =
      r / Radian(i)
