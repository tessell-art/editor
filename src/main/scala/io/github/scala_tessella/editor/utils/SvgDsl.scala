package io.github.scala_tessella.editor.utils

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.utils.Geometry.Point

object SvgDsl:

  object Defaults:
    val stroke            = "currentColor"
    val fill              = "currentColor"
    val strokeWidthThin   = "1"
    val strokeWidthMedium = "1.5"

  // Common svg root with width/height/viewBox
  def root(size: Int)(content: Mod[Element]*): Element =
    svg.svg(
      svg.width   := size.toString,
      svg.height  := size.toString,
      svg.viewBox := s"0 0 $size $size",
      content
    )

  def rootWH(width: Double, height: Double)(content: Mod[Element]*): Element =
    svg.svg(
      svg.width   := f"$width%1.4f",
      svg.height  := f"$height%1.4f",
      svg.viewBox := s"0 0 ${f"$width%1.4f"} ${f"$height%1.4f"}",
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

  def fmt3(d: Double) =
    f"$d%1.3f"

  def fmt4(d: Double) =
    f"$d%1.4f"

  def fmt6(d: Double) =
    f"$d%1.6f"

  def fmt3Point(point: Point): String =
    s"${fmt3(point.x)},${fmt3(point.y)}"

  def fmt6Point(point: Point): String =
    s"${fmt6(point.x)},${fmt6(point.y)}"

  def toPointsString(points: Seq[Point], decimals: Int = 3): String =
    points.map(fmt3Point).mkString(" ")

  // Simple line creator with defaults
  def line(
      x1: Double,
      y1: Double,
      x2: Double,
      y2: Double,
      stroke: String = Defaults.stroke,
      strokeW: String = Defaults.strokeWidthThin
  ): Element          =
    svg.line(
      svg.x1          := fmt3(x1),
      svg.y1          := fmt3(y1),
      svg.x2          := fmt3(x2),
      svg.y2          := fmt3(y2),
      svg.stroke      := stroke,
      svg.strokeWidth := strokeW
    )
