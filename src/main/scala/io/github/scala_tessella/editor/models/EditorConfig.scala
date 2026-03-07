package io.github.scala_tessella.editor.models

import io.github.scala_tessella.editor.utils.ColorRGB
import io.github.scala_tessella.editor.utils.geo.{LineSegment, Point}

object EditorConfig:
  // Canvas ViewBox dimensions
  val canvasViewBoxWidth  = 800
  val canvasViewBoxHeight = 600
  val canvasEnd: Point    = Point(canvasViewBoxWidth, canvasViewBoxHeight)

  // The scale factor from tiling coordinates to SVG coordinates
  val canvasScale = 50.0

  // Derived canvas center
  val canvasCenter: Point = LineSegment(Point.origin, canvasEnd).midPoint

  // View zoom limits
  val minViewScale: Double = 0.1
  val maxViewScale: Double = 5.0

  // View zoom factors by input modality
  val mouseWheelZoomInFactor: Double  = 1.1
  val mouseWheelZoomOutFactor: Double = 0.9
  val keyboardZoomFactor: Double      = 1.1
  val menuZoomFactor: Double          = 1.2

  // Polygon palette configuration
  val polygonSides: List[Int] = List(3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 18, 20, 24, 42)

  // Default polygon fill color when no explicit color is assigned
  val defaultPolygonColor: ColorRGB = ColorRGB(76, 175, 80)

  // Default perimeter edge color for the editor canvas
  val defaultPerimeterEdgeColor: ColorRGB = ColorRGB(255, 149, 0)

  // Fan animation duration (milliseconds)
  val fanAnimationDurationMs: Int = 3000
  val fanAnimationStaggerMs: Int  = 60
