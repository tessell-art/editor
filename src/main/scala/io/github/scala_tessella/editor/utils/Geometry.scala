package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.ring_seq.RingSeq.rotateLeft

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

    /** @see
      *   [[https://tauday.com/]]
      */
    val TAU: Radian   = Radian(6.283185307179586)
    val TAU_2: Radian = Radian(Math.PI)

    /** Normalize any angle to [0, TAU) */
    def normalize(a: Radian): Radian =
      val t = a % TAU
      if t < 0 then t + TAU else t

    /** Normalize delta angle to (-PI, PI] */
    def normalizeDelta(a: Radian): Radian =
      var d = a % TAU
      if d <= -TAU_2 then d += TAU
      if d > TAU_2 then d -= TAU
      d

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

    /** Operator alias */
    @targetName("pointPlus")
    def +(that: Point): Point =
      plus(that)

    /** Difference of two points */
    private def minus(that: Point): Point =
      Point(this.x - that.x, this.y - that.y)

    /** Operator alias (private semantics preserved for alignWithStart) */
    @targetName("pointMinus")
    private def -(that: Point): Point =
      minus(that)

    def scale(factor: Double): Point =
      Point(x * factor, y * factor)

    /** Operator aliases for scalars */
    @targetName("pointTimesScalar")
    def *(k: Double): Point =
      scale(k)

    @targetName("pointDivideScalar")
    def /(k: Double): Point =
      Point(x / k, y / k)

    def transform(scaleFactor: Double, offset: Point): Point =
      scale(scaleFactor).plus(offset)

    def rotate(theta: Radian): Point =
      val cot: Double =
        Math.cos(theta)
      val sit: Double =
        Math.sin(theta)
      Point(x * cot - y * sit, x * sit + y * cot)

    /** Rotate around an origin point */
    def rotateAround(origin: Point, theta: Radian): Point =
      this.minus(origin).rotate(theta).plus(origin)

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

    /** Get the length (magnitude) of this point as a vector */
    def magnitude: Double =
      Math.hypot(x, y)

    /** Normalize this point to unit length */
    def normalized: Point =
      val mag = magnitude
      val eps = 1e-12
      if mag < eps then Point(0, 0) else Point(x / mag, y / mag)

    /** Compute the dot product with another point (treating both as vectors) */
    def dot(that: Point): Double =
      this.x * that.x + this.y * that.y

//    /** New point flipped vertically around the x-axis */
//    def flipVertically: Point =
//      Point(x, -y)

  object Point:

    /** Creates a point at origin */
    def apply(): Point =
      Point(0, 0)

    /** Creates a point from polar coordinates */
    def createPolar(rho: Double, theta: Radian): Point =
      Point(rho * Math.cos(theta), rho * Math.sin(theta))

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

    /** Computes the horizontal angle of the line segment in [0, TAU) */
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

    def center: Point = Point((minX + maxX) / 2.0, (minY + maxY) / 2.0)

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

  /** Creates points for a regular polygon. */
  def regularPolygonPoints(sides: Int, radius: Double, center: Point = Point(0, 0)): Seq[Point] =
    (0 until sides).map { i =>
      val angle = (Radian.TAU * i / sides) - Radian.TAU_2 // Start from the top
      center.plus(Point.createPolar(radius, angle))
    }

  /** Build polygon vertices using unit edge length and given internal angles. */
  def buildUnitEdgePolygon(angles: Seq[Radian], startHeading: Radian = Radian(0)): Vector[Point] =
    if angles.isEmpty then Vector.empty
    else
      val pts     = Vector.newBuilder[Point]
      var heading = startHeading.toDouble
      var curr    = Point(0.0, 0.0)

      // first vertex
      pts += curr

      // Rotate the angles sequence to start from the second angle
//      val rotatedAngles = if angles.size > 1 then angles.tail ++ angles.take(1) else angles

      // For each interior angle:
      // 1) advance one unit in current heading to create next vertex
      // 2) then turn by the exterior angle (PI - interior)
      angles.rotateLeft(1).foreach { a =>
        val nx   = curr.x + Math.cos(heading)
        val ny   = curr.y + Math.sin(heading)
        val next = Point(nx, ny)
        pts += next
        curr = next
        heading = heading + (Math.PI - a)
      }

      // We now have N+1 points with the last equal to the first only for closed perfect polygons.
      // For preview we want exactly N vertices, so drop the last step-produced point.
      val built = pts.result()
      if built.size >= 2 then built.dropRight(1) else built

  /** Compute basic geometric properties of an edge (length, unit vector, midpoint). */
  def edgeGeometrics(vertex1: Point, vertex2: Point): (Double, Point, Point) =
    val dx         = vertex2.x - vertex1.x
    val dy         = vertex2.y - vertex1.y
    val edgeLen    = Math.hypot(dx, dy)
    val unitVector = if edgeLen == 0 then Point(0.0, 0.0) else Point(dx / edgeLen, dy / edgeLen)
    val midPoint   = Point((vertex1.x + vertex2.x) / 2, (vertex1.y + vertex2.y) / 2)
    (edgeLen, unitVector, midPoint)

  /** Generate perpendicular (normal) vectors to a given unit vector. Returns (left normal, right normal). */
  def perpendicularVectors(unitVector: Point): (Point, Point) =
    val leftNormal  = Point(-unitVector.y, unitVector.x) // Normal for CCW traversal
    val rightNormal = Point(unitVector.y, -unitVector.x) // Normal for CW traversal
    (leftNormal, rightNormal)

  /** Calculate geometric properties of a regular polygon (apothem, circumradius). */
  def regularPolygonMetrics(sides: Int, sideLength: Double): (Double, Double, Double) =
    val halfAngle = Math.PI / sides
    val apothem   = sideLength / (2 * Math.tan(halfAngle))
    val radius    = sideLength / (2 * Math.sin(halfAngle))
    (apothem, radius, halfAngle)

  /** Compute view-box (width, height, offX, offY) for a set of points with given scale and padding. */
  def fitPointsToViewBox(
      points: Seq[Point],
      scale: Double,
      padding: Double
  ): (Double, Double, Double, Double) =
    Bounds.fromPoints(points) match
      case None    => (2 * padding, 2 * padding, padding, padding)
      case Some(b) =>
        val width  = b.width * scale + 2 * padding
        val height = b.height * scale + 2 * padding
        val offX   = -b.minX * scale + padding
        val offY   = -b.minY * scale + padding
        (width, height, offX, offY)

  /** Walks a sequence of unit-length edges turning by given angles (in radians), returning vertices
    * (including start).
    */
  def walkUnitEdges(turns: Seq[Double]): Vector[Point] =
    var x       = 0.0
    var y       = 0.0
    var heading = 0.0 // radians
    val pts     = collection.mutable.ArrayBuffer[Point]()
    pts += Point(x, y)
    turns.foreach { t =>
      x = x + Math.cos(heading)
      y = y + Math.sin(heading)
      pts += Point(x, y)
      heading = heading + t
    }
    pts.toVector

  /** Compute view-box transform (scale, offX, offY) to fit points into a square of given size with padding.
    */
  def fitPointsToSquare(points: Seq[Point], size: Double, padding: Double): (Double, Double, Double) =
    Bounds.fromPoints(points) match
      case None    => (1.0, 0.0, 0.0)
      case Some(b) =>
        val w     = Math.max(1e-6, b.width)
        val h     = Math.max(1e-6, b.height)
        val scale = (size - 2 * padding) / Math.max(w, h)
        val offX  = (size - scale * w) / 2.0 - scale * b.minX
        val offY  = (size - scale * h) / 2.0 - scale * b.minY
        (scale, offX, offY)

  /** Normalize delta angle to (-PI, PI]. */
  def normalizeDeltaAngle(a2: Radian, a1: Radian): Radian =
    Radian.normalizeDelta(a2 - a1)

  // ---------------------------------------
  // Coordinate transformation utilities
  // ---------------------------------------

  /** Transform a point from tiling coordinates to canvas view coordinates using scale and offset. */
  def transformPointToView(point: Point, scale: Double, offsetX: Double, offsetY: Double): (Double, Double) =
    val transformed = point.transform(scale, Point(offsetX, offsetY))
    (transformed.x, transformed.y)

  /** Transform points for SVG generation with proper scaling and offsets. */
  def transformPointsForSvg(
      points: Seq[Point],
      scale: Double,
      offsetX: Double,
      offsetY: Double
  ): Seq[(Double, Double)] =
    points.map(p => transformPointToView(p, scale, offsetX, offsetY))

  /** Compute the midpoint of two points. */
  def midpoint(p1: Point, p2: Point): Point =
    LineSegment(p1, p2).midPoint

  /** Compute distance between two points. */
  def distance(p1: Point, p2: Point): Double =
    p1.distanceTo(p2)

  /** Compute the angle from one point to another. */
  def angleBetweenPoints(from: Point, to: Point): Radian =
    LineSegment(from, to).horizontalAngle
//    val dx = to.x - from.x
//    val dy = to.y - from.y
//    Radian((Math.atan2(dy, dx) + Radian.TAU) % Radian.TAU)
