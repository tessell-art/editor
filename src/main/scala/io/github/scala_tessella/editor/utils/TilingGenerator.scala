package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.tessella.Tiling
import io.github.scala_tessella.tessella.RegularPolygon.Polygon
import io.github.scala_tessella.editor.operations.ErrorOperations.showError
import io.github.scala_tessella.editor.operations.TessellationOperations.updateTiling

object TilingGenerator:

  /** Create a tiling from a polygon with the specified number of sides */
  def createTilingFromPolygon(sides: Int): Option[Tiling] =
    try
      Some(Tiling.fromPolygon(Polygon(sides)))
    catch
      case _: Exception => None
