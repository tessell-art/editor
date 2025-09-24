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

    def transform(scaleFactor: Double, offset: Point): Point =
      scale(scaleFactor).plus(offset)

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
    def angleTo(other: Point): Radian =
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

  // ---------------------------------------
  // New: general-purpose geometry utilities
  // ---------------------------------------

  case class Bounds(minX: Double, maxX: Double, minY: Double, maxY: Double):
    def width: Double = maxX - minX

    def height: Double = maxY - minY

  object Bounds:
    def fromPoints(points: Seq[Point]): Option[Bounds] =
      if points.isEmpty then None
      else
        val xs = points.map(_.x)
        val ys = points.map(_.y)
        Some(Bounds(xs.min, xs.max, ys.min, ys.max))

  extension (points: Seq[Point])
    def maybeBounds: Option[Bounds] =
      Bounds.fromPoints(points)

  /** Compute view-box (width, height, offX, offY) for a set of points with given scale and padding. */
  def fitPointsToViewBox(points: Seq[Point], scale: Double, padding: Double): (Double, Double, Double, Double) =
    Bounds.fromPoints(points) match
      case None => (2 * padding, 2 * padding, padding, padding)
      case Some(b) =>
        val width = b.width * scale + 2 * padding
        val height = b.height * scale + 2 * padding
        val offX = -b.minX * scale + padding
        val offY = -b.minY * scale + padding
        (width, height, offX, offY)

  /** Walks a sequence of unit-length edges turning by given angles (in radians), returning vertices (including start). */
  def walkUnitEdges(turns: Seq[Double]): Vector[Point] =
    var x = 0.0
    var y = 0.0
    var heading = 0.0 // radians
    val pts = collection.mutable.ArrayBuffer[Point]()
    pts += Point(x, y)
    turns.foreach { t =>
      x = x + Math.cos(heading)
      y = y + Math.sin(heading)
      pts += Point(x, y)
      heading = heading + t
    }
    pts.toVector

  /** Compute view-box transform (scale, offX, offY) to fit points into a square of given size with padding. */
  def fitPointsToSquare(points: Seq[Point], size: Double, padding: Double): (Double, Double, Double) =
    Bounds.fromPoints(points) match
      case None => (1.0, 0.0, 0.0)
      case Some(b) =>
        val w = Math.max(1e-6, b.width)
        val h = Math.max(1e-6, b.height)
        val scale = (size - 2 * padding) / Math.max(w, h)
        val offX = (size - scale * w) / 2.0 - scale * b.minX
        val offY = (size - scale * h) / 2.0 - scale * b.minY
        (scale, offX, offY)

  /** Normalize delta angle to (-PI, PI]. */
  def normalizeDeltaAngle(a2: Double, a1: Double): Double =
    var d = a2 - a1
    if d < -Math.PI then d += 2 * Math.PI
    if d > Math.PI then d -= 2 * Math.PI
    d