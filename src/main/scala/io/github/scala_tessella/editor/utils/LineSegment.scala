package io.github.scala_tessella.editor.utils

opaque type LineSegment = (p1: Point, p2: Point)

object LineSegment:

  def apply(p1: Point, p2: Point): LineSegment =
    (p1, p2)

  extension (segment: LineSegment)

    def p1: Point =
      segment.p1

    def p2: Point =
      segment.p2

    def dx: Double =
      p2.x - p1.x

    def dy: Double =
      p2.y - p1.y

    def midPoint: Point =
      (p1 + p2) / 2.0

    def length: Double =
      Math.hypot(dx, dy)

    def unitVector: Point =
      val len = segment.length
      if len == 0 then Point.origin
      else Point(segment.dx / len, segment.dy / len)

    def horizontalAngle: Radian =
      Radian(Math.atan2(dy, dx))
