
package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.tessella.Tiling
import io.github.scala_tessella.tessella.RegularPolygon.Polygon
import io.github.scala_tessella.editor.models.{AppState, Point, CanvasPolygon, CanvasText}

object TilingGenerator:
  def generateSampleTiling(): Option[Tiling] =
    try {
      Tiling.pattern_2x33344_2x3446_3636(6, 6).toOption
    } catch {
      case _: Exception => None
    }

  def generateSamplePolygons(): List[CanvasPolygon] =
    List(
      CanvasPolygon("poly1", 6, Point(200, 150), 40),
      CanvasPolygon("poly2", 4, Point(350, 200), 35, math.toRadians(45)),
      CanvasPolygon("poly3", 8, Point(150, 300), 50),
      CanvasPolygon("poly4", 3, Point(400, 120), 30),
      CanvasPolygon("poly5", 5, Point(300, 350), 45, math.toRadians(30))
    )

  def generateSampleTexts(): List[CanvasText] =
    List(
      CanvasText("text1", "Hexagon", Point(200, 200)),
      CanvasText("text2", "Square", Point(350, 250)),
      CanvasText("text3", "Octagon", Point(150, 360)),
      CanvasText("text4", "Triangle", Point(400, 170)),
      CanvasText("text5", "Pentagon", Point(300, 410))
    )

  def generateHexagonTiling(): Unit =
    try {
      val tiling = Tiling.pattern_666(3, 3).toOption.get
      AppState.currentTiling.set(Some(tiling))
    } catch {
      case _: Exception => println("Failed to generate hexagon tiling")
    }

  def generateTriangleTiling(): Unit =
    try {
      val tiling = Tiling.pattern_333333(3, 3).toOption.get
      AppState.currentTiling.set(Some(tiling))
    } catch {
      case _: Exception => println("Failed to generate triangle tiling")
    }

  def generateMixedTiling(): Unit =
    try {
      val tiling = Tiling.pattern_3464(3).toOption.get
      AppState.currentTiling.set(Some(tiling))
    } catch {
      case _: Exception => println("Failed to generate mixed tiling")
    }