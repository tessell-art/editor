package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.dcel.Polygon.RegularPolygon
import io.github.scala_tessella.dcel.TilingDCEL

object TilingBuilders:
  // Cached immutable instances for fast reuse
  lazy val triangle: TilingDCEL = TilingDCEL.createRegularPolygon(RegularPolygon(3))
  lazy val square: TilingDCEL   = TilingDCEL.createRegularPolygon(RegularPolygon(4))
  lazy val hexagon: TilingDCEL  = TilingDCEL.createRegularPolygon(RegularPolygon(6))

  // Fresh builders if you prefer a new instance each time
  def freshTriangle(): TilingDCEL = TilingDCEL.createRegularPolygon(RegularPolygon(3))
  def freshSquare(): TilingDCEL   = TilingDCEL.createRegularPolygon(RegularPolygon(4))
  def freshHexagon(): TilingDCEL  = TilingDCEL.createRegularPolygon(RegularPolygon(6))
