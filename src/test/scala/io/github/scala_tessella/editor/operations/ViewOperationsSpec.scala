package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.ViewTransform
import io.github.scala_tessella.editor.utils.Bounds
import io.github.scala_tessella.tessella.BigDecimalGeometry.AngleDegree
import io.github.scala_tessella.tessella.Geometry.Point
import munit.FunSuite

import scala.math.Pi

class ViewOperationsSpec extends FunSuite {

  private val delta = 1E-9

  test("anf") {
    val x = AngleDegree(90).toRadian
    assertEquals(x.toDouble, 1.5707963267948966)
  }

  test("transformCoordinates should rotate points correctly") {
    val points = List(Point(1, 0), Point(0, 1))
    val rotated = ViewOperations.transformCoordinates(points, 90)
    assert(Math.abs(rotated.head.x.toDouble - 0.0) < delta)
    assert(Math.abs(rotated.head.y.toDouble - 1.0) < delta)
    assert(Math.abs(rotated(1).x.toDouble - (-1.0)) < delta)
    assert(Math.abs(rotated(1).y.toDouble - 0.0) < delta)
  }

  test("transformCoordinates should handle zero rotation") {
    val points = List(Point(1, 2), Point(3, 4))
    val rotated = ViewOperations.transformCoordinates(points, 0)
    assertEquals(rotated, points)
  }

  test("calculateSafeDimensions should handle positive dimensions") {
    val (width, height) = ViewOperations.calculateSafeDimensions(100.0, 200.0)
    assertEquals(width, 100.0)
    assertEquals(height, 200.0)
  }

  test("calculateSafeDimensions should handle zero or negative dimensions") {
    assertEquals(ViewOperations.calculateSafeDimensions(0.0, 100.0), (1.0, 100.0))
    assertEquals(ViewOperations.calculateSafeDimensions(100.0, 0.0), (100.0, 1.0))
    assertEquals(ViewOperations.calculateSafeDimensions(-10.0, 100.0), (1.0, 100.0))
    assertEquals(ViewOperations.calculateSafeDimensions(100.0, -10.0), (100.0, 1.0))
  }

  test("calculateNewScale should calculate scale to fit width") {
    val scale = ViewOperations.calculateNewScale(canvasWidth = 200, canvasHeight = 200, tilingWidth = 100, tilingHeight = 50, padding = 25)
    // available width = 200 - 50 = 150. scaleX = 150 / 100 = 1.5
    // available height = 200 - 50 = 150. scaleY = 150 / 50 = 3
    // min is 1.5
    assertEquals(scale, 1.5)
  }

  test("calculateNewScale should calculate scale to fit height") {
    val scale = ViewOperations.calculateNewScale(canvasWidth = 200, canvasHeight = 200, tilingWidth = 50, tilingHeight = 100, padding = 25)
    // available width = 150. scaleX = 150 / 50 = 3
    // available height = 150. scaleY = 150 / 100 = 1.5
    // min is 1.5
    assertEquals(scale, 1.5)
  }

  test("calculateTilingCenter should return the center of the bounds") {
    val bounds = Bounds(minX = 10, minY = 20, maxX = 90, maxY = 80)
    val (centerX, centerY) = ViewOperations.calculateTilingCenter(bounds)
    assertEquals(centerX, 50.0)
    assertEquals(centerY, 50.0)
  }

  test("createUpdatedViewTransform should update scale and pan") {
    val initialTransform = ViewTransform(scale = 1.0, panX = 0, panY = 0, rotationDegrees = 0)
    val updatedTransform = ViewOperations.createUpdatedViewTransform(initialTransform, newScale = 2.0, newPanX = 100, newPanY = -100)
    assertEquals(updatedTransform.scale, 2.0)
    assertEquals(updatedTransform.panX, 100.0)
    assertEquals(updatedTransform.panY, -100.0)
    assertEquals(updatedTransform.rotationDegrees, 0) // Should not change
  }

  test("inverseTransform should correctly calculate world coordinates for the view center") {
    val (viewCenterX, viewCenterY) = (400.0, 300.0)
    val (panX, panY) = (50.0, -50.0)
    val scale = 2.0
    val rotationRad = Pi / 2 // 90 degrees

    val (worldX, worldY) = ViewOperations.inverseTransform(viewCenterX, viewCenterY, panX, panY, scale, rotationRad)

    // Manual calculation:
    // 1. Inverse Pan: (400-50, 300-(-50)) = (350, 350)
    // 2. Inverse Scale: (350/2, 350/2) = (175, 175)
    // 3. Inverse Rotation (rotate by -90 deg around viewCenter(400,300)):
    //    Point relative to center: (175-400, 175-300) = (-225, -125)
    //    Rotated relative point: x' = -225*cos(-90) - (-125)*sin(-90) = -225*0 - (-125)*(-1) = -125
    //                          y' = -225*sin(-90) + (-125)*cos(-90) = -225*(-1) + (-125)*0 = 225
    //    Final world point: (400 + (-125), 300 + 225) = (275, 525)

    assertEquals(worldX, 275.0, delta)
    assertEquals(worldY, 525.0, delta)
  }

  test("forwardTransform should correctly calculate screen coordinates") {
    val (worldX, worldY) = (275.0, 525.0)
    val (viewCenterX, viewCenterY) = (400.0, 300.0)
    val scale = 2.0
    val rotationRad = Pi / 2 // 90 degrees

    val (screenX, screenY) = ViewOperations.forwardTransform(worldX, worldY, viewCenterX, viewCenterY, scale, rotationRad)

    // Manual calculation:
    // 1. Forward Rotation (rotate by 90 deg around viewCenter(400,300)):
    //    Point relative to center: (275-400, 525-300) = (-125, 225)
    //    Rotated relative point: x' = -125*cos(90) - 225*sin(90) = -125*0 - 225*1 = -225
    //                          y' = -125*sin(90) + 225*cos(90) = -125*1 + 225*0 = -125
    //    After rotation point: (400 + (-225), 300 + (-125)) = (175, 175)
    // 2. Forward Scale: (175*2, 175*2) = (350, 350)
    
    assertEquals(screenX, 350.0, delta)
    assertEquals(screenY, 350.0, delta)
  }
  
  test("calculateRotatedPan should compute pan to keep a point stationary during rotation") {
    val (worldX, worldY) = (275.0, 525.0)
    val (viewCenterX, viewCenterY) = (400.0, 300.0)
    val scale = 2.0
    val newRotationRad = Pi / 2 // 90 degrees

    // After forward transform (rotation and scale), the point (worldX, worldY) moves to (350, 350) on screen, without pan.
    // We want it to be at (viewCenterX, viewCenterY) = (400, 300).
    // So, screen = pan + transformed => (400, 300) = (panX, panY) + (350, 350)
    // pan = (400-350, 300-350) = (50, -50)

    val (panX, panY) = ViewOperations.calculateRotatedPan(worldX, worldY, viewCenterX, viewCenterY, scale, newRotationRad)
    assertEquals(panX, 50.0, delta)
    assertEquals(panY, -50.0, delta)
  }

}
