package io.github.scala_tessella.editor.utils

import com.raquo.laminar.api.L.*
import com.raquo.laminar.modifiers.KeySetter
import io.github.scala_tessella.editor.utils.Point

object SvgDsl:

  object Defaults:
    val stroke            = "currentColor"
    val fill              = "currentColor"
    val strokeWidthThin   = "1"
    val strokeWidthMedium = "1.5"

  // Common svg root with width/height/viewBox
  def root(size: Int)(content: Mod[Element]*): Element =
    svg.svg(
      viewBoxCoords(Point(size, size)),
      content
    )

  def rootWH(point: Point)(content: Mod[Element]*): Element =
    svg.svg(
      viewBoxCoords(point),
      content
    )

  // Polygon from already-scaled points to string
  def polygon(
      pointsStr: String,
      fill: String = Defaults.fill,
      stroke: String = Defaults.stroke,
      strokeW: String = Defaults.strokeWidthThin
  ): Element      =
    svg.polygon(
      svg.points      := pointsStr,
      svg.fill        := fill,
      svg.stroke      := stroke,
      svg.strokeWidth := strokeW
    )

  // Helper: format points to "x,y" with n decimals
//  def fmtPoint(x: Double, y: Double, decimals: Int = 3): String =
//    val fx = f"$x%1.${decimals}f"
//    val fy = f"$y%1.${decimals}f"
//    s"$fx,$fy"

  def fmt(d: Double, decimals: Int): String =
    String.format(s"%1.${decimals}f", double2Double(d))

  def fmt3(d: Double): String =
    fmt(d, 3)

  def fmt4(d: Double): String =
    fmt(d, 4)

  def fmt6(d: Double): String =
    fmt(d, 6)

  def fmtPoint(point: Point, decimals: Int): String =
    s"${fmt(point.x, decimals)},${fmt(point.y, decimals)}"

  def fmt3Point(point: Point): String =
    fmtPoint(point, 3)

  def fmt6Point(point: Point): String =
    fmtPoint(point, 6)

  def toPointsString(points: Seq[Point], decimals: Int = 3): String =
    points.map(fmtPoint(_, decimals)).mkString(" ")

  def lineCoords(segment: LineSegment): Seq[KeySetter.SvgAttrSetter[String]] =
    Seq(
      svg.x1 := segment.p1.x.toString,
      svg.y1 := segment.p1.y.toString,
      svg.x2 := segment.p2.x.toString,
      svg.y2 := segment.p2.y.toString
    )

  def textCoords(point: Point): Seq[KeySetter.SvgAttrSetter[String]] =
    Seq(
      svg.x := point.x.toString,
      svg.y := point.y.toString
    )

  def widthHeightCoords(point: Point): Seq[KeySetter.SvgAttrSetter[String]] =
    Seq(
      svg.width  := point.x.toString,
      svg.height := point.y.toString
    )

  def rectCoords(segment: LineSegment): Seq[KeySetter.SvgAttrSetter[String]] =
    textCoords(segment.p1) ++ widthHeightCoords(segment.p2)

  def viewBoxCoords(point: Point): Seq[KeySetter.SvgAttrSetter[String]] =
    widthHeightCoords(point) :+ (svg.viewBox := s"0 0 ${point.x} ${point.y}")

  def circleCoordsRadius(point: Point, radius: Int): Seq[KeySetter.SvgAttrSetter[String]] =
    Seq(
      svg.cx := point.x.toString,
      svg.cy := point.y.toString,
      svg.r  := radius.toString
    )
