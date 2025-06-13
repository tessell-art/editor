package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.tessella.Tiling
import io.github.scala_tessella.tessella.RegularPolygon.Polygon
import io.github.scala_tessella.editor.models.AppState

object TilingGenerator:

  /** Create a tiling from a polygon with the specified number of sides */
  def createTilingFromPolygon(sides: Int): Option[Tiling] =
    try
      Some(Tiling.fromPolygon(Polygon(sides)))
    catch
      case _: Exception => None

  def generateSampleTiling(): Option[Tiling] =
    try
      Tiling.pattern_2x33344_2x3446_3636(6, 6).toOption
    catch
      case _: Exception => None

  def generateHexagonTiling(): Unit =
    try
      val tiling = Tiling.pattern_666(3, 3).toOption.get
      AppState.currentTiling.set(Some(tiling))
    catch
      case _: Exception =>
        AppState.showError("Failed to generate hexagon tiling")

  def generateTriangleTiling(): Unit =
    try
      val tiling = Tiling.pattern_333333(3, 3).toOption.get
      AppState.currentTiling.set(Some(tiling))
    catch
      case _: Exception =>
        AppState.showError("Failed to generate triangle tiling")

  def generateMixedTiling(): Unit =
    try
      val tiling = Tiling.pattern_3464(3).toOption.get
      AppState.currentTiling.set(Some(tiling))
    catch
      case _: Exception =>
        AppState.showError("Failed to generate mixed tiling")