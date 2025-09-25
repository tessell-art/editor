package io.github.scala_tessella.editor.models

import munit.FunSuite

class ViewTransformSpec extends FunSuite:

  test("ViewTransform should have correct default values") {
    val transform = ViewTransform()
    assertEquals(transform.scale, 1.0)
    assertEquals(transform.rotationDegrees, 0)
    assertEquals(transform.panX, 0.0)
    assertEquals(transform.panY, 0.0)
  }

  test("normalizeRotation should handle positive angles correctly") {
    val transform = ViewTransform()
    assertEquals(transform.normalizeRotation(45), 45)
    assertEquals(transform.normalizeRotation(359), 359)
    assertEquals(transform.normalizeRotation(360), 0)
    assertEquals(transform.normalizeRotation(450), 90)
  }

  test("normalizeRotation should handle negative angles correctly") {
    val transform = ViewTransform()
    assertEquals(transform.normalizeRotation(-45), 315)
    assertEquals(transform.normalizeRotation(-360), 0)
    assertEquals(transform.normalizeRotation(-450), 270)
  }

  test("normalizeRotation should handle zero angle") {
    val transform = ViewTransform()
    assertEquals(transform.normalizeRotation(0), 0)
  }

  test("withRotation should normalize rotation angles") {
    val transform = ViewTransform()
    val rotated   = transform.withRotation(450)
    assertEquals(rotated.rotationDegrees, 90)

    val negativeRotated = transform.withRotation(-45)
    assertEquals(negativeRotated.rotationDegrees, 315)
  }

  test("withRotation should preserve other properties") {
    val transform = ViewTransform(scale = 2.0, panX = 10.0, panY = 20.0)
    val rotated   = transform.withRotation(45)

    assertEquals(rotated.scale, 2.0)
    assertEquals(rotated.panX, 10.0)
    assertEquals(rotated.panY, 20.0)
    assertEquals(rotated.rotationDegrees, 45)
  }

  test("copy should work correctly") {
    val original = ViewTransform(scale = 1.5, rotationDegrees = 90, panX = 5.0, panY = 10.0)
    val copied   = original.copy(scale = 2.0)

    assertEquals(copied.scale, 2.0)
    assertEquals(copied.rotationDegrees, 90)
    assertEquals(copied.panX, 5.0)
    assertEquals(copied.panY, 10.0)
  }

  test("multiple rotation operations should work correctly") {
    val transform = ViewTransform()
    val result    = transform
      .withRotation(45)
      .withRotation(360)
      .withRotation(-90)

    assertEquals(result.rotationDegrees, 270)
  }
