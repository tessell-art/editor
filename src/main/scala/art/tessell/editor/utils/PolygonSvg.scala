package art.tessell.editor.utils

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.geometry.AngleDegree
import art.tessell.editor.utils.geo.Geometry.{
  buildUnitEdgePolygon,
  fitPointsToSquare,
  regularPolygonPoints
}
import SvgDsl.{lineCoords, polygon, root, textCoords}
import art.tessell.editor.utils.geo.{LineSegment, Point, Radian}

object PolygonSvg:

  // Regular polygon thumbnail (used in palette buttons)
  def regularPreview(sides: Int, size: Int = 40, radiusFactor: Double = 0.35): Element =
    val center: Point = Point(size / 2.0, size / 2.0)
    val radius        = size * radiusFactor
    val pts           = regularPolygonPoints(sides, radius, center)
    root(size)(
      polygon(pts)
    )

  // Irregular polygon from AngleDegree edges, unit walk + fit. For genuinely irregular shapes
  // (i.e., not all-equal angles) we add a single small dot just outside the attaching edge
  // midpoint so the palette button shows which side the polygon will mate from on placement —
  // miniature parity with the popup's white chevron and the canvas snap hint.
  def irregularPreview(anglesDeg: Vector[AngleDegree], size: Int = 40, pad: Double = 4.0): Element =
    val turns           = anglesDeg.map(_.supplement.toBigRadian.toBigDecimal.toDouble).map(Radian(_))
    val basePts         = buildUnitEdgePolygon(turns)
    val (scale, offset) = basePts.fitPointsToSquare(size, pad)
    val fitted          = basePts.map(_.scaleAndTranslate(scale, offset))
    val isRegular       = anglesDeg.nonEmpty && anglesDeg.forall(_ == anglesDeg.head)
    val attachingDot    =
      if isRegular || fitted.size < 3 then None
      else
        val a       = fitted(1)
        val b       = fitted((1 + 1) % fitted.size)
        val mid     = (a + b) / 2.0
        val tangent = (b - a).normalized
        val perpA   = tangent.perp
        val probe   = mid + (perpA * 0.5)
        val outward = if pointInsidePolygon(probe, fitted) then perpA * -1.0 else perpA
        val dotPos  = mid + (outward * 2.0)
        Some(svg.circle(
          svg.className := "attaching-dot",
          svg.cx        := dotPos.x.toString,
          svg.cy        := dotPos.y.toString,
          svg.r         := "1.5",
          svg.fill      := "currentColor"
        ))
    root(size)(
      SvgDsl.polygon(fitted),
      attachingDot.getOrElse(emptyNode)
    )

  // Big irregular with attaching edge overlay
  def irregularBigWithHead(anglesDeg: Vector[AngleDegree], size: Int = 220, pad: Double = 12.0): Element =
    val turns           = anglesDeg.map(_.supplement.toBigRadian.toBigDecimal.toDouble).map(Radian(_))
    val basePts         = buildUnitEdgePolygon(turns)
    val (scale, offset) = basePts.fitPointsToSquare(size, pad)

    def bigTransform(p: Point): Point =
      p.scaleAndTranslate(scale, offset)

    val points  = basePts.map(bigTransform)
    val headIdx = ((1 % basePts.size) + basePts.size) % basePts.size
    val a       = basePts(headIdx)
    val b       = basePts((headIdx + 1) % basePts.size)

    val orderedAngles =
      if (anglesDeg.isEmpty) Vector.empty
      else anglesDeg.last +: anglesDeg.init

    val angleLabels = basePts.zip(orderedAngles).map { case (point, angle) =>
      val label: Point   = bigTransform(point) + Point(4.0, -4.0)
      svg.text(
        textCoords(label),
        svg.fontSize   := "12",
        svg.fontFamily := "monospace",
        svg.fill       := "red",
        svg.textAnchor := "start",
        s"${angle.toRational.toDouble}°"
      )
    }
    val a2          = bigTransform(a)
    val b2          = bigTransform(b)

    // White chevron at the attaching-edge midpoint, pointing outward — visual parity with the
    // drag-from-palette snap hint (`PaletteSnapHintRenderer`). For non-convex polygons (those with
    // reflex angles ≥180°) the arithmetic centroid can sit such that `mid − centroid` points into a
    // concavity rather than out of the polygon, so we don't trust the centroid here. Instead we
    // probe a tiny step along each candidate perpendicular and ask point-in-polygon (ray-casting)
    // which side is interior — the OTHER side is outward, regardless of winding or shape.
    val mid              = (a2 + b2) / 2.0
    val tangent          = (b2 - a2).normalized
    val perpA            = tangent.perp
    val probe            = mid + (perpA * 0.5)
    val outward          =
      if pointInsidePolygon(probe, points) then perpA * -1.0 else perpA
    val chevronOffset    = 4.0
    val chevronLength    = 12.0
    val chevronHalfWidth = 7.0
    val baseCenter       = mid + (outward * chevronOffset)
    val apex             = baseCenter + (outward * chevronLength)
    val baseLeft         = baseCenter + (tangent * chevronHalfWidth)
    val baseRight        = baseCenter - (tangent * chevronHalfWidth)
    val chevronStr       =
      Vector(apex, baseLeft, baseRight)
        .map { case (x, y) =>
          s"$x,$y"
        }
        .mkString(" ")

    root(size)(
      SvgDsl.polygon(points, strokeW = SvgDsl.Defaults.strokeWidthMedium),
      svg.line(
        lineCoords(LineSegment(a2, b2)),
        svg.stroke        := "#00C853",
        svg.strokeWidth   := "6",
        svg.strokeLineCap := "round",
        svg.pointerEvents := "none"
      ),
      svg.polygon(
        svg.className     := "attaching-edge-chevron",
        svg.points        := chevronStr,
        svg.pointerEvents := "none"
      ),
      svg.g(
        angleLabels
      )
    )

  /** Standard even-odd ray-casting test: does the horizontal ray from `p` extending to +∞ cross the polygon's
    * edges an odd number of times? Robust for non-convex simple polygons.
    */
  private def pointInsidePolygon(p: Point, polygon: Vector[Point]): Boolean =
    val n = polygon.size
    if n < 3 then false
    else
      var inside = false
      var j      = n - 1
      var i      = 0
      while i < n do
        val xi = polygon(i).x
        val yi = polygon(i).y
        val xj = polygon(j).x
        val yj = polygon(j).y
        if (yi > p.y) != (yj > p.y) then
          val intersectX = (xj - xi) * (p.y - yi) / (yj - yi) + xi
          if p.x < intersectX then inside = !inside
        j = i
        i += 1
      inside
