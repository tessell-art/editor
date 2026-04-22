package io.github.scala_tessella.editor.components

import munit.FunSuite

class GridRendererSpec extends FunSuite:

  test("strokeWidthForScale returns 1/scale for scales in the visible range"):
    assertEquals(GridRenderer.strokeWidthForScale(1.0), "1.0")
    assertEquals(GridRenderer.strokeWidthForScale(2.0), "0.5")
    assertEquals(GridRenderer.strokeWidthForScale(4.0), "0.25")

  test("strokeWidthForScale clamps to a maximum of 2.0 when zoomed far out"):
    assertEquals(GridRenderer.strokeWidthForScale(0.5), "2.0")
    assertEquals(GridRenderer.strokeWidthForScale(0.1), "2.0")
    assertEquals(GridRenderer.strokeWidthForScale(0.01), "2.0")

  test("strokeWidthForScale clamps to a minimum of 0.1 when zoomed far in"):
    assertEquals(GridRenderer.strokeWidthForScale(10.0), "0.1")
    assertEquals(GridRenderer.strokeWidthForScale(100.0), "0.1")
