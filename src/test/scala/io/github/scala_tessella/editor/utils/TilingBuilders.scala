package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.dcel.TilingDCEL

object TilingBuilders:
  // Cached immutable instances for fast reuse
  lazy val triangle: TilingDCEL = TilingDCEL.createRegularPolygon(3).toOption.get
  lazy val square: TilingDCEL   = TilingDCEL.createRegularPolygon(4).toOption.get
  lazy val hexagon: TilingDCEL  = TilingDCEL.createRegularPolygon(6).toOption.get

  // Fresh builders if you prefer a new instance each time
  def freshTriangle(): TilingDCEL = TilingDCEL.createRegularPolygon(3).toOption.get
  def freshSquare(): TilingDCEL   = TilingDCEL.createRegularPolygon(4).toOption.get
  def freshHexagon(): TilingDCEL  = TilingDCEL.createRegularPolygon(6).toOption.get
