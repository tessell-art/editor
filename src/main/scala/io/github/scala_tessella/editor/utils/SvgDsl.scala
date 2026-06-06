package io.github.scala_tessella.editor.utils

import com.raquo.laminar.api.L.*
import com.raquo.laminar.modifiers.KeySetter
import io.github.scala_tessella.editor.utils.geo.{LineSegment, Point}

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
      points: Seq[Point],
      fill: String = Defaults.fill,
      stroke: String = Defaults.stroke,
      strokeW: String = Defaults.strokeWidthThin
  ): Element =
    svg.polygon(
      polygonCoords(points),
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

  def polygonCoords(points: Seq[Point]): Seq[KeySetter.SvgAttrSetter[String]] =
    Seq(
      svg.points := toPointsString(points)
    )

  def lineCoords(segment: LineSegment): Seq[KeySetter.SvgAttrSetter[String]] =
    Seq(
      svg.x1 := segment.p1.x.toString,
      svg.y1 := segment.p1.y.toString,
      svg.x2 := segment.p2.x.toString,
      svg.y2 := segment.p2.y.toString
    )

  /** A small filled arrowhead at the midpoint of the `from`→`to` segment, pointing towards `to`. Shared by
    * the Measurement line and the Add Copy ▸ Translate vector so both read identically (ADR-014). Renders
    * nothing when the two points coincide.
    */
  def midArrow(
      from: Point,
      to: Point,
      colour: String = "#ffffff",
      size: Double = 7.0,
      halfWidth: Double = 4.0
  ): Modifier[Element] =
    val dx  = to.x - from.x
    val dy  = to.y - from.y
    val len = math.hypot(dx, dy)
    if len < 1e-6 then emptyMod
    else
      val (ux, uy) = (dx / len, dy / len)
      val (px, py) = (-uy, ux) // unit perpendicular
      val mx       = (from.x + to.x) / 2
      val my       = (from.y + to.y) / 2
      val tip      = (mx + ux * size / 2, my + uy * size / 2)
      val base     = (mx - ux * size / 2, my - uy * size / 2)
      val left     = (base._1 + px * halfWidth, base._2 + py * halfWidth)
      val right    = (base._1 - px * halfWidth, base._2 - py * halfWidth)
      svg.polygon(
        svg.points := s"${tip._1},${tip._2} ${left._1},${left._2} ${right._1},${right._2}",
        svg.fill   := colour
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

  def uniformColorMap: Map[Int, String] =
    Map(
      0  -> "yellow",
      1  -> "orange",
      2  -> "violet",
      3  -> "green",
      4  -> "brown",
      5  -> "pink",
      6  -> "deeppink",
      7  -> "darkkhaki",
      8  -> "blueviolet",
      9  -> "lime",
      10 -> "lightgreen",
      11 -> "lightblue",
      12 -> "lightcoral",
      13 -> "lightseagreen",
      14 -> "lightskyblue",
      15 -> "lightsalmon",
      16 -> "yellowgreen",
      17 -> "lightgoldenrodyellow",
      18 -> "lightgray",
      19 -> "slategray",
      20 -> "crimson",
      21 -> "tomato",
      22 -> "goldenrod",
      23 -> "darkorange",
      24 -> "olive",
      25 -> "seagreen",
      26 -> "teal",
      27 -> "steelblue",
      28 -> "royalblue",
      29 -> "navy",
      30 -> "indigo",
      31 -> "mediumvioletred",
      32 -> "sienna",
      33 -> "chocolate",
      34 -> "peru",
      35 -> "darkturquoise",
      36 -> "cadetblue",
      37 -> "mediumseagreen",
      38 -> "cornflowerblue",
      39 -> "darkmagenta",
      40 -> "firebrick",
      41 -> "darkgoldenrod",
      42 -> "forestgreen",
      43 -> "mediumaquamarine",
      44 -> "darkcyan",
      45 -> "dodgerblue",
      46 -> "slateblue",
      47 -> "orchid",
      48 -> "darkslategray",
      49 -> "maroon"
    )
