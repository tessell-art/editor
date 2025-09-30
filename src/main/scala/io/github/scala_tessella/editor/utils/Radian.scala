package io.github.scala_tessella.editor.utils

import scala.annotation.targetName

/** Standard unit of angular measure */
opaque type Radian = Double

/** Companion object for [[Radian]] */
object Radian:

  /** Create a [[Radian]] from a `Double` */
  def apply(d: Double): Radian =
    d

  def fromDegrees(degrees: Double): Radian =
    Math.toRadians(degrees)

  /** @see
    *   [[https://tauday.com/]]
    */
  val TAU: Radian   = Radian(6.283185307179586)
  val TAU_2: Radian = Radian(Math.PI)

  extension (r: Radian)

    /** @return the underlying `Double` */
    def toDouble: Double =
      r

    def toDegrees: Double =
      Math.toDegrees(r)

    /** Normalize any angle to [0, TAU) */
    def normalize: Radian =
      val t = r % Radian.TAU
      if t < 0 then t + Radian.TAU else t

    /** Normalize delta angle to (-PI, PI] */
    def normalizeDelta: Radian =
      val d = r % Radian.TAU
      if d <= -Radian.TAU_2 then
        d + Radian.TAU
      else if d > Radian.TAU_2 then
        d - Radian.TAU
      else
        d

    /** Normalize delta angle to (-PI, PI]. */
    def normalizeDeltaAngle(other: Radian): Radian =
      (r - other).normalizeDelta

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
