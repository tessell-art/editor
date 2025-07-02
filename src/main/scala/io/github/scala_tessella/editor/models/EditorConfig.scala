package io.github.scala_tessella.editor.models

object EditorConfig:
  // Canvas ViewBox dimensions
  val canvasViewBoxWidth = 800
  val canvasViewBoxHeight = 600

  // The scale factor from tiling coordinates to SVG coordinates
  val canvasScale = 50.0

  // Derived canvas center
  val canvasCenterX: Double = canvasViewBoxWidth / 2.0
  val canvasCenterY: Double = canvasViewBoxHeight / 2.0

  // Polygon palette configuration
  val polygonSides: List[Int] = List(3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 18, 20, 24, 42)
