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

  opaque type Point2 = (x: Double, y: Double)

  object Point2:

    def apply(x: Double, y: Double): Point2 =
      (x, y)

    def origin: Point2 =
      (0.0, 0.0)

    /** Creates a point from polar coordinates */
    def createPolar(rho: Double, theta: Radian): Point2 =
      (rho * Math.cos(theta), rho * Math.sin(theta))

  opaque type LineSegment2 = (p1: Point2, p2: Point2)

  object LineSegment2:

    def apply(p1: Point2, p2: Point2): LineSegment2 =
      (p1, p2)

  extension (point: Point2)

    def xx: Double =
      point.x

    def yy: Double =
      point.y

    def plus(that: Point2): Point2 =
      (point.x + that.x, point.y + that.y)

    @targetName("point2Plus")
    def +(that: Point2): Point2 =
      plus(that)

    def minus(that: Point2): Point2 =
      (point.x - that.x, point.y - that.y)

    @targetName("point2Minus")
    def -(that: Point2): Point2 =
      minus(that)

    def scale(factor: Double): Point2 =
      (point.x * factor, point.y * factor)

    /** Operator aliases for scalars */
    @targetName("point2TimesScalar")
    def *(k: Double): Point2 =
      scale(k)

    @targetName("point2DivideScalar")
    def /(k: Double): Point2 =
      (point.x / k, point.y / k)

    def transform(scaleFactor: Double, offset: Point2): Point2 =
      scale(scaleFactor) + offset

    def rotate(theta: Radian): Point2 =
      val cot: Double =
        Math.cos(theta)
      val sit: Double =
        Math.sin(theta)
      (point.x * cot - point.y * sit, point.x * sit + point.y * cot)

    /** Rotate around an origin point */
    def rotateAround(origin: Point2, theta: Radian): Point2 =
      (point - origin).rotate(theta) + origin

    /** Calculates the horizontal angle between two points */
    def angleTo(other: Point2): Radian =
      (point, other).horizontalAngle

    def distanceTo(other: Point2): Double =
      (point, other).length

    /** New point moved to align with reference to two other points */
    def alignWithStart(first: Point2, second: Point2): Point2 =
      (point - first).rotate(Radian.TAU - first.angleTo(second))

    /** Get the length (magnitude) of this point as a vector */
    def magnitude: Double =
      Math.hypot(point.x, point.y)

    /** Normalize this point to unit length */
    def normalized: Point2 =
      val mag = magnitude
      val eps = 1e-12
      if mag < eps then (0, 0) else (point.x / mag, point.y / mag)

    /** Compute the dot product with another point (treating both as vectors) */
    def dot(that: Point2): Double =
      point.x * that.x + point.y * that.y

  extension (segment: LineSegment2)

    def dx: Double =
      segment.p2.x - segment.p1.x

    def dy: Double =
      segment.p2.y - segment.p1.y

    def midPoint: Point2 =
      (segment.p1 + segment.p2) / 2.0

    def length: Double =
      Math.hypot(dx, dy)

    def unitVector: Point2 =
      val len = segment.length
      if len == 0 then Point2.origin
      else (segment.dx / len, segment.dy / len)

    /** Computes the horizontal angle of the line segment in [0, TAU) */
    def horizontalAngle: Radian =
      Radian((Math.atan2(dy, dx) + Radian.TAU) % Radian.TAU)

  case class Bounds(min: Point2, max: Point2):
    def width: Double = max.x - min.x

    def height: Double = max.y - min.y

    def diagonal: LineSegment2 = (min, max)

    def center: Point2 = diagonal.midPoint

  object Bounds:
    def fromPoints(points: Seq[Point2]): Option[Bounds] =
      if points.isEmpty then None
      else
        val xs = points.map(_.x)
        val ys = points.map(_.y)
        Some(Bounds((xs.min, ys.min), (xs.max, ys.max)))

  extension (points: Seq[Point2])
    def maybeBounds: Option[Bounds] =
      Bounds.fromPoints(points)

    /** Transform points for SVG generation with proper scaling and offsets. */
    def transformPointsForSvg(
        scale: Double,
        offset: Point2
    ): Seq[Point2] =
      points.map(_.transform(scale, offset))

  /** Creates points for a regular polygon. */
  def regularPolygonPoints(sides: Int, radius: Double, center: Point2 = Point2.origin): Seq[Point2] =
    (0 until sides).map { i =>
      val angle = (Radian.TAU * i / sides) - Radian.TAU_2 // Start from the top
      center + Point2.createPolar(radius, angle)
    }

  /** Build polygon vertices using unit edge length and given internal angles. */
  def buildUnitEdgePolygon(angles: Seq[Radian], startHeading: Radian = Radian(0)): Vector[Point2] =
    if angles.isEmpty then Vector.empty
    else
      val pts          = Vector.newBuilder[Point2]
      var heading      = startHeading.toDouble
      var curr: Point2 = Point2.origin

      // first vertex
      pts += curr

      // Rotate the angle sequence to start from the second angle
//      val rotatedAngles = if angles.size > 1 then angles.tail ++ angles.take(1) else angles

      // For each interior angle:
      // 1) advance one unit in current heading to create next vertex
      // 2) then turn by the exterior angle (PI - interior)
      angles.rotateLeft(1).foreach { a =>
        val nx   = curr.x + Math.cos(heading)
        val ny   = curr.y + Math.sin(heading)
        val next = (nx, ny)
        pts += next
        curr = next
        heading = heading + (Math.PI - a)
      }

      // We now have N+1 points with the last equal to the first only for closed perfect polygons.
      // For preview we want exactly N vertices, so drop the last step-produced point.
      val built = pts.result()
      if built.size >= 2 then built.dropRight(1) else built

  /** Compute basic geometric properties of an edge (length, unit vector, midpoint). */
  def edgeGeometrics(vertex1: Point2, vertex2: Point2): (Double, Point2, Point2) =
    val segment: LineSegment2 = (vertex1, vertex2)
    (segment.length, segment.unitVector, segment.midPoint)

  /** Generate perpendicular (normal) vectors to a given unit vector. Returns (left normal, right normal). */
  def perpendicularVectors(unitVector: Point2): (Point2, Point2) =
    val leftNormal  = (-unitVector.y, unitVector.x) // Normal for CCW traversal
    val rightNormal = (unitVector.y, -unitVector.x) // Normal for CW traversal
    (leftNormal, rightNormal)

  /** Calculate geometric properties of a regular polygon (apothem, circumradius). */
  def regularPolygonMetrics(sides: Int, sideLength: Double): (Double, Double, Double) =
    val halfAngle = Math.PI / sides
    val apothem   = sideLength / (2 * Math.tan(halfAngle))
    val radius    = sideLength / (2 * Math.sin(halfAngle))
    (apothem, radius, halfAngle)

  /** Compute view-box (width, height, offX, offY) for a set of points with given scale and padding. */
  def fitPointsToViewBox(
      points: Seq[Point2],
      scale: Double,
      padding: Double
  ): (Double, Double, Point2) =
    Bounds.fromPoints(points) match
      case None    => (2 * padding, 2 * padding, Point2(padding, padding))
      case Some(b) =>
        val width  = b.width * scale + 2 * padding
        val height = b.height * scale + 2 * padding
        val offX   = -b.min.x * scale + padding
        val offY   = -b.min.y * scale + padding
        (width, height, Point2(offX, offY))

  /** Walks a sequence of unit-length edges turning by given angles (in radians), returning vertices
    * (including start).
    */
  def walkUnitEdges(turns: Seq[Double]): Vector[Point2] =
    val origin  = Point2.origin
    var x       = origin.x
    var y       = origin.y
    var heading = 0.0 // radians
    val pts     = collection.mutable.ArrayBuffer[Point2](origin)
    turns.foreach { t =>
      x = x + Math.cos(heading)
      y = y + Math.sin(heading)
      pts += Point2(x, y)
      heading = heading + t
    }
    pts.toVector

  /** Compute view-box transform (scale, offX, offY) to fit points into a square of given size with padding.
    */
  def fitPointsToSquare(points: Seq[Point2], size: Double, padding: Double): (Double, Point2) =
    Bounds.fromPoints(points) match
      case None    => (1.0, Point2.origin)
      case Some(b) =>
        val w     = Math.max(1e-6, b.width)
        val h     = Math.max(1e-6, b.height)
        val scale = (size - 2 * padding) / Math.max(w, h)
        val offX  = (size - scale * w) / 2.0 - scale * b.min.x
        val offY  = (size - scale * h) / 2.0 - scale * b.min.y
        (scale, Point2(offX, offY))

  /** Normalize delta angle to (-PI, PI]. */
  def normalizeDeltaAngle(a2: Radian, a1: Radian): Radian =
    Radian.normalizeDelta(a2 - a1)

  // ---------------------------------------
  // Coordinate transformation utilities
  // ---------------------------------------
  /** Compute the midpoint of two points. */
  def midpoint(p1: Point2, p2: Point2): Point2 =
    val segment: LineSegment2 = (p1, p2)
    segment.midPoint

  /** Compute distance between two points. */
  def distance(p1: Point2, p2: Point2): Double =
    p1.distanceTo(p2)

  /** Compute the angle from one point to another. */
  def angleBetweenPoints(from: Point2, to: Point2): Radian =
    val segment: LineSegment2 = (from, to)
    segment.horizontalAngle
//    val dx = to.x - from.x
//    val dy = to.y - from.y
//    Radian((Math.atan2(dy, dx) + Radian.TAU) % Radian.TAU)
