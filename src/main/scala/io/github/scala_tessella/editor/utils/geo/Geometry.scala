package io.github.scala_tessella.editor.utils.geo

/** Planar geometry simplified toolbox */
object Geometry:

  case class Bounds(min: Point, max: Point):
    def width: Double = max.x - min.x

    def height: Double = max.y - min.y

    def diagonal: LineSegment = LineSegment(min, max)

    def center: Point = diagonal.midPoint

  object Bounds:
    def fromPoints(points: Seq[Point]): Option[Bounds] =
      if points.isEmpty then None
      else
        val xs = points.map(_.x)
        val ys = points.map(_.y)
        Some(Bounds(Point(xs.min, ys.min), Point(xs.max, ys.max)))

  extension (points: Seq[Point])
    def maybeBounds: Option[Bounds] =
      Bounds.fromPoints(points)

    /** Transform points for SVG generation with proper scaling and offsets. */
    def transformPointsForSvg(
        scale: Double,
        offset: Point
    ): Seq[Point] =
      points.map(_.scaleAndTranslate(scale, offset))

    /** Compute the view-box (width, height, offset) for a set of points with given scale and padding. */
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
          val width  = Math.max(1e-6, b.width)
          val height = Math.max(1e-6, b.height)
          val scale  = (size - 2 * padding) / Math.max(width, height)
          val offX   = (size - scale * width) / 2.0 - scale * b.min.x
          val offY   = (size - scale * height) / 2.0 - scale * b.min.y
          (scale, Point(offX, offY))

  /** Creates points for a regular polygon. */
  def regularPolygonPoints(sides: Int, radius: Double, center: Point = Point.origin): Seq[Point] =
    // shift to have a flat bottom side
    val shift =
      if sides % 2 != 0 then Radian.TAU_2 / 2
      else Radian.TAU_2 / 2 + Radian.TAU_2 / sides
    (0 until sides).map { i =>
      val angle = (Radian.TAU * i / sides) - shift // Start from the top
      center + Point.fromPolar(radius, angle)
    }

  /** Build polygon vertices using unit edge length and given internal angles. */
  def buildUnitEdgePolygon(angles: Seq[Radian]): Vector[Point] =
    val origin  = Point.origin
    var x       = origin.x
    var y       = origin.y
    var heading = 0.0 // radians
    val pts     = collection.mutable.ArrayBuffer[Point](origin)
    angles.foreach { t =>
      x = x + Math.cos(heading)
      y = y + Math.sin(heading)
      pts += Point(x, y)
      heading = heading + t.toDouble
    }
    pts.toVector

  /** Compute basic geometric properties of an edge (length, unit vector, midpoint). */
  def edgeMetrics(vertex1: Point, vertex2: Point): (Double, Point, Point) =
    val segment: LineSegment = LineSegment(vertex1, vertex2)
    (segment.length, segment.unitVector, segment.midPoint)

  /** Calculate geometric properties of a regular polygon (apothem, circumradius). */
  def regularPolygonMetrics(sides: Int, sideLength: Double): (Double, Double, Radian) =
    val halfAngle = Radian.TAU_2 / sides
    val apothem   = sideLength / (2 * Math.tan(halfAngle.toDouble))
    val radius    = sideLength / (2 * Math.sin(halfAngle.toDouble))
    (apothem, radius, halfAngle)
