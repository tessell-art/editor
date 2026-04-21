package io.github.scala_tessella.editor.utils.geo

import scala.annotation.targetName

opaque type Point = (x: Double, y: Double)

object Point:

  val origin: Point =
    (0.0, 0.0)

  inline def apply(x: Double, y: Double): Point =
    (x, y)

  /** Creates a point from polar coordinates */
  def fromPolar(rho: Double, theta: Radian): Point =
    (rho * Math.cos(theta.toDouble), rho * Math.sin(theta.toDouble))

  extension (point: Point)

    inline def x: Double =
      point.x

    inline def y: Double =
      point.y

    def plus(that: Point): Point =
      (point.x + that.x, point.y + that.y)

    @targetName("point2Plus")
    def +(that: Point): Point =
      plus(that)

    def minus(that: Point): Point =
      (point.x - that.x, point.y - that.y)

    @targetName("point2Minus")
    def -(that: Point): Point =
      minus(that)

    def scale(factor: Double): Point =
      (point.x * factor, point.y * factor)

    /** Operator aliases for scalars */
    @targetName("point2TimesScalar")
    def *(k: Double): Point =
      scale(k)

    @targetName("pointTimes")
    def *(that: Point): Point =
      (point.x * that.x, point.y * that.y)

    @targetName("point2DivideScalar")
    def /(k: Double): Point =
      (point.x / k, point.y / k)

    @targetName("pointDivide")
    def /(that: Point): Point =
      (point.x / that.x, point.y / that.y)

    def scaleAndTranslate(scaleFactor: Double, offset: Point): Point =
      scale(scaleFactor) + offset

    def rotate(theta: Radian): Point =
      val cot: Double =
        Math.cos(theta.toDouble)
      val sit: Double =
        Math.sin(theta.toDouble)
      (point.x * cot - point.y * sit, point.x * sit + point.y * cot)

    /** Rotate around an origin point */
    def rotateAround(origin: Point, theta: Radian): Point = (point - origin).rotate(theta) + origin

    def offsetPolar(radius: Double, theta: Radian): Point =
      (point.x + radius * Math.cos(theta.toDouble), point.y + radius * Math.sin(theta.toDouble))

    /** Calculates the horizontal angle between two points */
    def angleTo(other: Point): Radian =
      LineSegment(point, other).horizontalAngle

    def distanceTo(other: Point): Double =
      LineSegment(point, other).length

    /** New point moved to align with reference to two other points */
    def alignWithStart(first: Point, second: Point): Point = (point - first).rotate(Radian.TAU -
      first.angleTo(second))

    /** Get the length (magnitude) of this point as a vector */
    def magnitude: Double =
      Math.hypot(point.x, point.y)

    /** Normalize this point to unit length */
    def normalized: Point =
      val mag = magnitude
      val eps = 1e-12
      if mag < eps then (0, 0) else (point.x / mag, point.y / mag)

    /** Compute the dot product with another point (treating both as vectors) */
    def dot(that: Point): Double =
      point.x * that.x + point.y * that.y

    /** Perpendicular vector rotated 90° CCW */
    def perp: Point =
      (-point.y, point.x)

    /** Perpendicular vector rotated 90° CW */
    def perpCW: Point =
      (point.y, -point.x)

    /** Generate perpendicular (normal) vectors to a given unit vector. Returns (left normal, right normal).
      */
    def perpendicularVectors: (Point, Point) =
      (perp, perpCW)

    /** Projection of this vector onto axis */
    def projectOn(axis: Point): Point =
      val u = axis.normalized
      u * dot(u)
