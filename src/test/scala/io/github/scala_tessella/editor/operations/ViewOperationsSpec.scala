package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.BigDecimalGeometry.AngleDegree
import io.github.scala_tessella.editor.models.ViewTransform
import io.github.scala_tessella.editor.utils.Geometry.{Bounds, Point, Radian}
import munit.FunSuite

import scala.math.Pi

class ViewOperationsSpec extends FunSuite:

  private val delta = 1e-9

  test("anf") {
    val x = AngleDegree(90).toBigRadian
    assertEquals(x.toBigDecimal.toDouble, 1.5707963267948966, delta)
  }

  test("transformCoordinates should rotate points correctly") {
    val points  = List(Point(1, 0), Point(0, 1))
    val rotated = ViewOperations.transformCoordinates(points, 90)
    assert(Math.abs(rotated.head.xx - 0.0) < delta)
    assert(Math.abs(rotated.head.yy - 1.0) < delta)
    assert(Math.abs(rotated(1).xx - -1.0) < delta)
    assert(Math.abs(rotated(1).yy - 0.0) < delta)
  }

  test("transformCoordinates should handle zero rotation") {
    val points  = List(Point(1, 2), Point(3, 4))
    val rotated = ViewOperations.transformCoordinates(points, 0)
    assertEquals(rotated, points)
  }

  test("calculateSafeDimensions should handle positive dimensions") {
    val (width, height) = ViewOperations.calculateSafeDimensions(100.0, 200.0)
    assertEquals(width, 100.0, delta)
    assertEquals(height, 200.0, delta)
  }

  test("calculateSafeDimensions should handle zero or negative dimensions") {
    assertEquals(ViewOperations.calculateSafeDimensions(0.0, 100.0), (1.0, 100.0))
    assertEquals(ViewOperations.calculateSafeDimensions(100.0, 0.0), (100.0, 1.0))
    assertEquals(ViewOperations.calculateSafeDimensions(-10.0, 100.0), (1.0, 100.0))
    assertEquals(ViewOperations.calculateSafeDimensions(100.0, -10.0), (100.0, 1.0))
  }

  test("calculateNewScale should calculate scale to fit width") {
    val scale = ViewOperations.calculateNewScale(
      canvasWidth = 200,
      canvasHeight = 200,
      tilingWidth = 100,
      tilingHeight = 50,
      padding = 25
    )
    // available width = 200 - 50 = 150. scaleX = 150 / 100 = 1.5
    // available height = 200 - 50 = 150. scaleY = 150 / 50 = 3
    // min is 1.5
    assertEquals(scale, 1.5, delta)
  }

  test("calculateNewScale should calculate scale to fit height") {
    val scale = ViewOperations.calculateNewScale(
      canvasWidth = 200,
      canvasHeight = 200,
      tilingWidth = 50,
      tilingHeight = 100,
      padding = 25
    )
    // available width = 150. scaleX = 150 / 50 = 3
    // available height = 150. scaleY = 150 / 100 = 1.5
    // min is 1.5
    assertEquals(scale, 1.5, delta)
  }

  test("calculateTilingCenter should return the center of the bounds") {
    val bounds = Bounds(Point(10, 20), Point(90, 80))
    val center = bounds.center
    assertEquals(center.xx, 50.0, delta)
    assertEquals(center.yy, 50.0, delta)
  }

  test("createUpdatedViewTransform should update scale and pan") {
    val initialTransform = ViewTransform(scale = 1.0, pan = Point.origin, rotationDegrees = 0)
    val updatedTransform = ViewOperations.createUpdatedViewTransform(
      initialTransform,
      newScale = 2.0,
      newPan = Point(100, -100)
    )
    assertEquals(updatedTransform.scale, 2.0, delta)
    assertEquals(updatedTransform.pan.xx, 100.0, delta)
    assertEquals(updatedTransform.pan.yy, -100.0, delta)
    assertEquals(updatedTransform.rotationDegrees, 0) // Should not change
  }

  test("inverseTransform should correctly calculate world coordinates for the view center") {
    val viewCenter  = Point(400.0, 300.0)
    val pan         = Point(50.0, -50.0)
    val scale       = 2.0
    val rotationRad = Radian.TAU_2 / 2 // 90 degrees

    val world =
      ViewOperations.inverseTransform(viewCenter, pan, scale, rotationRad)

    // Manual calculation:
    // 1. Inverse Pan: (400-50, 300-(-50)) = (350, 350)
    // 2. Inverse Scale: (350/2, 350/2) = (175, 175)
    // 3. Inverse Rotation (rotate by -90 deg around viewCenter(400,300)):
    //    Point relative to center: (175-400, 175-300) = (-225, -125)
    //    Rotated relative point: x' = -225*cos(-90) - (-125)*sin(-90) = -225*0 - (-125)*(-1) = -125
    //                          y' = -225*sin(-90) + (-125)*cos(-90) = -225*(-1) + (-125)*0 = 225
    //    Final world point: (400 + (-125), 300 + 225) = (275, 525)

    assertEquals(world.xx, 275.0, delta)
    assertEquals(world.yy, 525.0, delta)
  }

  test("forwardTransform should correctly calculate screen coordinates") {
    val world       = Point(275.0, 525.0)
    val viewCenter  = Point(400.0, 300.0)
    val scale       = 2.0
    val rotationRad = Radian.TAU_2 / 2 // 90 degrees

    val screen =
      ViewOperations.forwardTransform(world, viewCenter, scale, rotationRad)

    // Manual calculation:
    // 1. Forward Rotation (rotate by 90 deg around viewCenter(400,300)):
    //    Point relative to center: (275-400, 525-300) = (-125, 225)
    //    Rotated relative point: x' = -125*cos(90) - 225*sin(90) = -125*0 - 225*1 = -225
    //                          y' = -125*sin(90) + 225*cos(90) = -125*1 + 225*0 = -125
    //    After rotation point: (400 + (-225), 300 + (-125)) = (175, 175)
    // 2. Forward Scale: (175*2, 175*2) = (350, 350)

    assertEquals(screen.xx, 350.0, delta)
    assertEquals(screen.yy, 350.0, delta)
  }

  test("calculateRotatedPan should compute pan to keep a point stationary during rotation") {
    val world          = Point(275.0, 525.0)
    val viewCenter     = Point(400.0, 300.0)
    val scale          = 2.0
    val newRotationRad = Radian.TAU_2 / 2 // 90 degrees

    // After forward transform (rotation and scale), the point (worldX, worldY) moves to (350, 350) on screen, without pan.
    // We want it to be at (viewCenterX, viewCenterY) = (400, 300).
    // So, screen = pan + transformed => (400, 300) = (panX, panY) + (350, 350)
    // pan = (400-350, 300-350) = (50, -50)

    val pan =
      ViewOperations.calculateRotatedPan(world, viewCenter, scale, newRotationRad)
    assertEquals(pan.xx, 50.0, delta)
    assertEquals(pan.yy, -50.0, delta)
  }

  val initialTransform: ViewTransform = ViewTransform(1.0, 0, Point.origin)
  val testCanvasWidth                 = 800.0
  val testCanvasHeight                = 600.0
  val padding                         = 50.0
  val coords: List[Point]             = List(Point(10, 20), Point(110, 70)) // width=100, height=50

  test("should return a new transform when fitting a tiling to the canvas") {
    val result = ViewOperations.calculateFitToCanvasTransform(
      coords,
      testCanvasWidth,
      testCanvasHeight,
      initialTransform,
      padding
    )

    assert(result.isDefined)
    val transform = result.get

    // Manually calculate expected values
    // bounds: minX=10, minY=20, maxX=110, maxY=70
    // tilingWidth=100, tilingHeight=50
    // scaleX = (800 - 100) / 100 = 7.0
    // scaleY = (600 - 100) / 50 = 10.0
    // newScale = 7.0
    assertEquals(transform.scale, 7.0, delta)

    // tilingCenterX = (10+110)/2 = 60, tilingCenterY = (20+70)/2 = 45
    // EditorConfig.canvasCenterX = 400, EditorConfig.canvasCenterY = 300
    // tilingCenterOnCanvasX = 60 + 400 = 460
    // tilingCenterOnCanvasY = 45 + 300 = 345
    // newPanX = 400 - 460 * 7.0 = 400 - 3220 = -2820
    // newPanY = 300 - 345 * 7.0 = 300 - 2415 = -2115
    assertEquals(transform.pan.xx, -2820.0, delta)
    assertEquals(transform.pan.yy, -2115.0, delta)
    assertEquals(transform.rotationDegrees, 0)
  }

  test("should calculate transform based on viewBox, ignoring canvas dimensions") {
    // Since the transform is based on the SVG's viewBox, not the canvas element's size,
    // the function should return a valid transform even with invalid canvas dimensions.
    val resultWithInvalidCanvas =
      ViewOperations.calculateFitToCanvasTransform(coords, 0, -10, initialTransform, padding)

    val expectedResult = ViewOperations.calculateFitToCanvasTransform(
      coords,
      testCanvasWidth,
      testCanvasHeight,
      initialTransform,
      padding
    )

    assert(
      resultWithInvalidCanvas.isDefined,
      "A transform should be returned even for invalid canvas dimensions"
    )
    assertEquals(
      resultWithInvalidCanvas,
      expectedResult,
      "The result should be the same regardless of canvas dimensions"
    )
  }

  test("should return None if coords are empty") {
    val result = ViewOperations.calculateFitToCanvasTransform(
      Nil,
      testCanvasWidth,
      testCanvasHeight,
      initialTransform,
      padding
    )
    assertEquals(result, None)
  }

  test("should return None if tiling has no width or height") {
    val singlePointCoords = List(Point(10, 20))
    val result            = ViewOperations.calculateFitToCanvasTransform(
      singlePointCoords,
      testCanvasWidth,
      testCanvasHeight,
      initialTransform,
      padding
    )
    assertEquals(result, None)
  }

  test("should correctly calculate transform with rotation") {
    val rotatedTransform = initialTransform.copy(rotationDegrees = 90)
    // coords: List(Point(10, 20), Point(110, 70))
    // after 90 deg rotation -> List(Point(-20, 10), Point(-70, 110))
    // rotated bounds: minX=-70, minY=10, maxX=-20, maxY=110
    // tilingWidth=50, tilingHeight=100
    val result           = ViewOperations.calculateFitToCanvasTransform(
      coords,
      testCanvasWidth,
      testCanvasHeight,
      rotatedTransform,
      padding
    )

    assert(result.isDefined)
    val transform = result.get

    // scaleX = (800 - 100) / 50 = 14
    // scaleY = (600 - 100) / 100 = 5
    // newScale = 5.0
    assertEquals(transform.scale, 5.0, delta)

    // After rotation:
    // rotatedCoords: List(Point(-20, 10), Point(-70, 110)) approx.
    // bounds: minX=-70, minY=10, maxX=-20, maxY=110
    // tilingCenterX = (-70-20)/2 = -45
    // tilingCenterY = (10+110)/2 = 60
    // tilingCenterOnCanvasX = -45 + 400 = 355
    // tilingCenterOnCanvasY = 60 + 300 = 360
    // newPanX = 400 - 355 * 5.0 = 400 - 1775 = -1375
    // newPanY = 300 - 360 * 5.0 = 300 - 1800 = -1500
    assertEquals(transform.pan.xx, -1375.0, delta)
    assertEquals(transform.pan.yy, -1500.0, delta)
    assertEquals(transform.rotationDegrees, 90)
  }
