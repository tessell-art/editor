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

  opaque type Point = (x: Double, y: Double)

  object Point:

    def apply(x: Double, y: Double): Point =
      (x, y)

    def origin: Point =
      (0.0, 0.0)

    /** Creates a point from polar coordinates */
    def createPolar(rho: Double, theta: Radian): Point =
      (rho * Math.cos(theta), rho * Math.sin(theta))

  opaque type LineSegment = (p1: Point, p2: Point)

  object LineSegment:

    def apply(p1: Point, p2: Point): LineSegment =
      (p1, p2)

  extension (point: Point)

    def xx: Double =
      point.x

    def yy: Double =
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

    @targetName("point2DivideScalar")
    def /(k: Double): Point =
      (point.x / k, point.y / k)

    def transform(scaleFactor: Double, offset: Point): Point =
      scale(scaleFactor) + offset

    def rotate(theta: Radian): Point =
      val cot: Double =
        Math.cos(theta)
      val sit: Double =
        Math.sin(theta)
      (point.x * cot - point.y * sit, point.x * sit + point.y * cot)

    /** Rotate around an origin point */
    def rotateAround(origin: Point, theta: Radian): Point =
      (point - origin).rotate(theta) + origin

    /** Calculates the horizontal angle between two points */
    def angleTo(other: Point): Radian =
      (point, other).horizontalAngle

    def distanceTo(other: Point): Double =
      (point, other).length

    /** New point moved to align with reference to two other points */
    def alignWithStart(first: Point, second: Point): Point =
      (point - first).rotate(Radian.TAU - first.angleTo(second))

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

  extension (segment: LineSegment)

    def dx: Double =
      segment.p2.x - segment.p1.x

    def dy: Double =
      segment.p2.y - segment.p1.y

    def midPoint: Point =
      (segment.p1 + segment.p2) / 2.0

    def length: Double =
      Math.hypot(dx, dy)

    def unitVector: Point =
      val len = segment.length
      if len == 0 then Point.origin
      else (segment.dx / len, segment.dy / len)

    /** Computes the horizontal angle of the line segment in [0, TAU) */
    def horizontalAngle: Radian =
      Radian((Math.atan2(dy, dx) + Radian.TAU) % Radian.TAU)

  case class Bounds(min: Point, max: Point):
    def width: Double = max.x - min.x

    def height: Double = max.y - min.y

    def diagonal: LineSegment = (min, max)

    def center: Point = diagonal.midPoint

  object Bounds:
    def fromPoints(points: Seq[Point]): Option[Bounds] =
      if points.isEmpty then None
      else
        val xs = points.map(_.x)
        val ys = points.map(_.y)
        Some(Bounds((xs.min, ys.min), (xs.max, ys.max)))

  extension (points: Seq[Point])
    def maybeBounds: Option[Bounds] =
      Bounds.fromPoints(points)

    /** Transform points for SVG generation with proper scaling and offsets. */
    def transformPointsForSvg(
        scale: Double,
        offset: Point
    ): Seq[Point] =
      points.map(_.transform(scale, offset))

    /** Compute view-box (width, height, offset) for a set of points with given scale and padding. */
    def fitPointsToViewBox(
        scale: Double,
        padding: Double
    ): (Double, Double, Point) = {
      val pointPadding = Point(padding, padding)
      Bounds.fromPoints(points) match
        case None    => (2 * padding, 2 * padding, pointPadding)
        case Some(b) =>
          val width  = b.width * scale + 2 * padding
          val height = b.height * scale + 2 * padding
          val off    = b.min * -scale + pointPadding
          (width, height, off)
    }

    /** Compute view-box transform (scale, offset) to fit points into a square of given size with padding.
      */
    def fitPointsToSquare(size: Double, padding: Double): (Double, Point) =
      Bounds.fromPoints(points) match
        case None    => (1.0, Point.origin)
        case Some(b) =>
          val w     = Math.max(1e-6, b.width)
          val h     = Math.max(1e-6, b.height)
          val scale = (size - 2 * padding) / Math.max(w, h)
          val offX  = (size - scale * w) / 2.0 - scale * b.min.x
          val offY  = (size - scale * h) / 2.0 - scale * b.min.y
          (scale, Point(offX, offY))

  /** Creates points for a regular polygon. */
  def regularPolygonPoints(sides: Int, radius: Double, center: Point = Point.origin): Seq[Point] =
    (0 until sides).map { i =>
      val angle = (Radian.TAU * i / sides) - Radian.TAU_2 // Start from the top
      center + Point.createPolar(radius, angle)
    }

  /** Build polygon vertices using unit edge length and given internal angles. */
  def buildUnitEdgePolygon(angles: Seq[Radian], startHeading: Radian = Radian(0)): Vector[Point] =
    if angles.isEmpty then Vector.empty
    else
      val pts         = Vector.newBuilder[Point]
      var heading     = startHeading.toDouble
      var curr: Point = Point.origin

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
  def edgeGeometrics(vertex1: Point, vertex2: Point): (Double, Point, Point) =
    val segment: LineSegment = (vertex1, vertex2)
    (segment.length, segment.unitVector, segment.midPoint)

  /** Generate perpendicular (normal) vectors to a given unit vector. Returns (left normal, right normal). */
  def perpendicularVectors(unitVector: Point): (Point, Point) =
    val leftNormal  = (-unitVector.y, unitVector.x) // Normal for CCW traversal
    val rightNormal = (unitVector.y, -unitVector.x) // Normal for CW traversal
    (leftNormal, rightNormal)

  /** Calculate geometric properties of a regular polygon (apothem, circumradius). */
  def regularPolygonMetrics(sides: Int, sideLength: Double): (Double, Double, Double) =
    val halfAngle = Math.PI / sides
    val apothem   = sideLength / (2 * Math.tan(halfAngle))
    val radius    = sideLength / (2 * Math.sin(halfAngle))
    (apothem, radius, halfAngle)

  /** Walks a sequence of unit-length edges turning by given angles (in radians), returning vertices
    * (including start).
    */
  def walkUnitEdges(turns: Seq[Double]): Vector[Point] =
    val origin  = Point.origin
    var x       = origin.x
    var y       = origin.y
    var heading = 0.0 // radians
    val pts     = collection.mutable.ArrayBuffer[Point](origin)
    turns.foreach { t =>
      x = x + Math.cos(heading)
      y = y + Math.sin(heading)
      pts += Point(x, y)
      heading = heading + t
    }
    pts.toVector

  /** Normalize delta angle to (-PI, PI]. */
  def normalizeDeltaAngle(a2: Radian, a1: Radian): Radian =
    Radian.normalizeDelta(a2 - a1)
