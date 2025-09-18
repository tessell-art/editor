package io.github.scala_tessella.editor.utils

import scala.annotation.targetName

/** Planar geometry simplified toolbox */
object Geometry:

  /** Standard unit of angular measure */
  opaque type Radian = Double

  /** Companion object for [[Radian]] */
  object Radian:

    /** Create a [[Radian]] from a `Double` */
    def apply(d: Double): Radian =
      d

    /**
     * @see [[https://tauday.com/]]
     */
    val TAU: Radian = Radian(6.283185307179586)
    val TAU_2: Radian = Radian(Math.PI)

  extension (r: Radian)

    /** @return the underlying `Double` */
    def toDouble: Double =
      r

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

  /** A point in the plane defined by its 2 Cartesian coordinates x and y */
  case class Point(x: Double, y: Double):

    /** Sum of two points */
    def plus(that: Point): Point =
      Point(this.x + that.x, this.y + that.y)

    /** Difference of two points */
    private def minus(that: Point): Point =
      Point(this.x - that.x, this.y - that.y)

    def scale(factor: Double): Point =
      Point(x * factor, y * factor)

    def rotate(theta: Radian): Point =
      val cot: Double =
        Math.cos(theta)
      val sit: Double =
        Math.sin(theta)
      Point(x * cot - y * sit, x * sit + y * cot)

//    /** New point moved by polar coordinates
//     *
//     * @param rho   distance
//     * @param theta angle
//     */
//    def plusPolar(rho: Double)(theta: Radian): Point =
//      plus(Point.createPolar(rho, theta))

//    /** New point moved by distance 1.0 */
//    def plusPolarUnit: Radian => Point =
//      plusPolar(1)

    /** Calculates the horizontal angle between two points */
    private def angleTo(other: Point): Radian =
      LineSegment(this, other).horizontalAngle

    def distanceTo(other: Point): Double =
      LineSegment(this, other).length

    /** New point moved to align with reference to two other points */
    def alignWithStart(first: Point, second: Point): Point =
      minus(first).rotate(Radian.TAU - first.angleTo(second))

//    /** New point flipped vertically around the x-axis */
//    def flipVertically: Point =
//      Point(x, -y)

//  object Point:
//
//    /** Creates a point at origin */
//    def apply(): Point =
//      Point(0, 0)
//
//    /** Creates a point from polar coordinates */
//    def createPolar(rho: Double, theta: Radian): Point =
//      Point(rho * Math.cos(theta), rho * Math.sin(theta))

  /** Line segment, defined as the set of points located between the two end points. */
  case class LineSegment(point1: Point, point2: Point):

    private val dx: Double =
      point2.x - point1.x

    private val dy: Double =
      point2.y - point1.y

    def midPoint: Point =
      Point((point1.x + point2.x) / 2, (point1.y + point2.y) / 2)

    def length: Double =
      Math.hypot(dx, dy)

    /** Computes the horizontal angle of the line segment */
    def horizontalAngle: Radian =
      Radian((Math.atan2(dy, dx) + Radian.TAU) % Radian.TAU)

//    /** Checks if at least one endpoint is contained in the given box */
//    def hasEndpointIn(box: Box): Boolean =
//      box.contains(point1) || box.contains(point2)
//
//  /** Bounds of a shape. */
//  case class Box(x0: Double, x1: Double, y0: Double, y1: Double):
//
//    def contains(point: Point): Boolean =
//      if point.x < x0 then false
//      else if point.y < y0 then false
//      else if point.x > x1 then false
//      else !(point.y > y1)
//
//    def enlarge(d: Double): Box =
//      Box(x0 - d, x1 +d, y0 - d, y1 + d)
//
//    def width: Double =
//      x1 - x0
//
//    def height: Double =
//      y1 - y0
