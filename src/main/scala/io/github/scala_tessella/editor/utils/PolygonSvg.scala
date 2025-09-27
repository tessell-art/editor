package io.github.scala_tessella.editor.utils

import com.raquo.laminar.api.L.*
import io.github.scala_tessella.dcel.BigDecimalGeometry.AngleDegree
import io.github.scala_tessella.editor.utils.Geometry.{
  Point2,
  fitPointsToSquare,
  regularPolygonPoints,
  walkUnitEdges
}
import io.github.scala_tessella.editor.utils.SvgDsl.{fmt3, polygon, root}

object PolygonSvg:

  // Generic: render an arbitrary polygon preview fitted to a square
  def previewFitted(
      points: Vector[Point2],
      size: Int,
      pad: Double,
      strokeW: String = SvgDsl.Defaults.strokeWidthThin
  ): Element =
    val (scale, offX, offY) = fitPointsToSquare(points, size, pad)
    val scaled              = points.map(p => Point2(offX + p.xx * scale, offY + p.yy * scale))
    val pointsStr           = SvgDsl.toPointsString(scaled)
    root(size)(
      SvgDsl.polygon(pointsStr, strokeW = strokeW)
    )

  // Regular polygon thumbnail (used in palette buttons)
  def regularPreview(sides: Int, size: Int = 40, radiusFactor: Double = 0.35): Element =
    val center: Point2 = Point2(size / 2.0, size / 2.0)
    val radius         = size * radiusFactor
    val pts            = regularPolygonPoints(sides, radius, center)
    val pointsStr      = SvgDsl.toPointsString(pts)
    root(size)(
      polygon(pointsStr)
    )

  // Irregular polygon from AngleDegree edges, unit walk + fit
  def irregularPreview(anglesDeg: Vector[AngleDegree], size: Int = 40, pad: Double = 4.0): Element =
    val turns = anglesDeg.map(_.supplement.toBigRadian.toBigDecimal.toDouble)
    val pts   = walkUnitEdges(turns)
    previewFitted(pts, size, pad)

  // Big irregular with attaching edge overlay
  def irregularBigWithHead(anglesDeg: Vector[AngleDegree], size: Int = 220, pad: Double = 12.0): Element =
    val turns               = anglesDeg.map(_.supplement.toBigRadian.toBigDecimal.toDouble)
    val basePts             = walkUnitEdges(turns)
    val (scale, offX, offY) =
      io.github.scala_tessella.editor.utils.Geometry.fitPointsToSquare(basePts, size, pad)

    def sx(p: Point2) = offX + p.xx * scale

    def sy(p: Point2) = offY + p.yy * scale

    val pointsStr = basePts.map(p => SvgDsl.fmt3Point(Point2(sx(p), sy(p)))).mkString(" ")
    val headIdx   = ((1 % basePts.size) + basePts.size) % basePts.size
    val a         = basePts(headIdx)
    val b         = basePts((headIdx + 1) % basePts.size)

    val orderedAngles =
      if (anglesDeg.isEmpty) Vector.empty
      else anglesDeg.last +: anglesDeg.init

    val angleLabels = basePts.zip(orderedAngles).map { case (point, angle) =>
      val labelX          = sx(point) + 4
      val labelY          = sy(point) - 4
      svg.text(
        svg.x          := fmt3(labelX),
        svg.y          := fmt3(labelY),
        svg.fontSize   := "12",
        svg.fontFamily := "monospace",
        svg.fill       := "red",
        svg.textAnchor := "start",
        s"${angle.toRational.toDouble}°"
      )
    }

    root(size)(
      SvgDsl.polygon(pointsStr, strokeW = SvgDsl.Defaults.strokeWidthMedium),
      svg.line(
        svg.x1            := fmt3(sx(a)),
        svg.y1            := fmt3(sy(a)),
        svg.x2            := fmt3(sx(b)),
        svg.y2            := fmt3(sy(b)),
        svg.stroke        := "#00C853",
        svg.strokeWidth   := "6",
        svg.strokeLineCap := "round",
        svg.pointerEvents := "none"
      ),
      svg.g(
        angleLabels
      )
    )
