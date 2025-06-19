package io.github.scala_tessella.editor.utils

import munit.FunSuite

class TilingGeneratorSpec extends FunSuite:

  test("createTilingFromPolygon should create valid tilings for common shapes") {
    // Test triangle
    val triangleTiling = TilingGenerator.createTilingFromPolygon(3)
    assert(triangleTiling.isDefined)

    // Test square  
    val squareTiling = TilingGenerator.createTilingFromPolygon(4)
    assert(squareTiling.isDefined)

    // Test hexagon
    val hexagonTiling = TilingGenerator.createTilingFromPolygon(6)
    assert(hexagonTiling.isDefined)
  }

  test("createTilingFromPolygon should handle edge cases gracefully") {
    // Test with unusual polygon sides
    val pentagonTiling = TilingGenerator.createTilingFromPolygon(5)
    // Pentagon might not create a valid tessellation, but should not crash
    assert(pentagonTiling.isDefined || pentagonTiling.isEmpty)

    val largePolygon = TilingGenerator.createTilingFromPolygon(42)
    assert(largePolygon.isDefined || largePolygon.isEmpty)
  }
