package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.tessella.IncrementalTiling
import io.github.scala_tessella.tessella.RegularPolygon.Polygon

object TilingGenerator:

  /** Create a tiling from a polygon with the specified number of sides */
  def createTilingFromPolygon(sides: Int): Option[TilingDCEL] =
    try {
      TilingDCEL.createRegularPolygon(sides).toOption
    } catch
      case _: Exception => None
