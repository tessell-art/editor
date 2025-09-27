package io.github.scala_tessella.editor.models

import io.github.scala_tessella.editor.utils.Geometry.{LineSegment2, Point2}

object EditorConfig:
  // Canvas ViewBox dimensions
  val canvasViewBoxWidth  = 800
  val canvasViewBoxHeight = 600
  val canvasEnd: Point2   = Point2(canvasViewBoxWidth, canvasViewBoxHeight)

  // The scale factor from tiling coordinates to SVG coordinates
  val canvasScale = 50.0

  // Derived canvas center
  val canvasCenter: Point2 = LineSegment2(Point2.origin, canvasEnd).midPoint

  // Polygon palette configuration
  val polygonSides: List[Int] = List(3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 18, 20, 24, 42)
