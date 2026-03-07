package io.github.scala_tessella.editor.interactions

import io.github.scala_tessella.editor.models.{EditorConfig, ViewTransform}
import io.github.scala_tessella.editor.utils.geo.Point
import munit.FunSuite

class MouseEventHandlerSpec extends FunSuite:

  private def worldPoint(
      screen: Point,
      transform: ViewTransform
  ): Point =
    (screen - transform.pan) / transform.scale

  test("calculateZoomTransform keeps the world point under the cursor"):
    val transform = ViewTransform(scale = 1.0, rotationDegrees = 0, pan = Point.origin)
    val cursor    = Point(400, 300)
    val before    = worldPoint(cursor, transform)

    val afterTransform =
      MouseEventHandler.calculateZoomTransform(transform, cursor, scaleFactor = 1.1)
    val after          = worldPoint(cursor, afterTransform)

    assertEquals(before.x, after.x)
    assertEquals(before.y, after.y)

  test("calculateZoomTransform clamps scale to min and max"):
    val cursor = Point(10, 10)
    val minT   = ViewTransform(scale = EditorConfig.minViewScale, rotationDegrees = 0, pan = Point.origin)
    val maxT   = ViewTransform(scale = EditorConfig.maxViewScale, rotationDegrees = 0, pan = Point.origin)

    val zoomOut = MouseEventHandler.calculateZoomTransform(minT, cursor, scaleFactor = 0.5)
    val zoomIn  = MouseEventHandler.calculateZoomTransform(maxT, cursor, scaleFactor = 2.0)

    assertEquals(zoomOut.scale, EditorConfig.minViewScale)
    assertEquals(zoomIn.scale, EditorConfig.maxViewScale)
