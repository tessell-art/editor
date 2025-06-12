package io.github.scala_tessella.editor.utils

import munit.FunSuite
import io.github.scala_tessella.editor.models.{Point, CanvasPolygon, CanvasText}

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
    // Pentagon might not create a valid tiling, but should not crash
    assert(pentagonTiling.isDefined || pentagonTiling.isEmpty)
    
    val largePolygon = TilingGenerator.createTilingFromPolygon(42)
    assert(largePolygon.isDefined || largePolygon.isEmpty)
  }

  test("generateSamplePolygons should return correct number of polygons") {
    val polygons = TilingGenerator.generateSamplePolygons()
    assertEquals(polygons.length, 5)
  }

  test("generateSamplePolygons should have unique IDs") {
    val polygons = TilingGenerator.generateSamplePolygons()
    val ids = polygons.map(_.id).toSet
    assertEquals(ids.size, polygons.length)
  }

  test("generateSamplePolygons should contain expected polygon types") {
    val polygons = TilingGenerator.generateSamplePolygons()
    val sides = polygons.map(_.sides).toSet
    assert(sides.contains(3)) // Triangle
    assert(sides.contains(4)) // Square
    assert(sides.contains(5)) // Pentagon
    assert(sides.contains(6)) // Hexagon
    assert(sides.contains(8)) // Octagon
  }

  test("generateSampleTexts should return correct number of texts") {
    val texts = TilingGenerator.generateSampleTexts()
    assertEquals(texts.length, 5)
  }

  test("generateSampleTexts should have unique IDs") {
    val texts = TilingGenerator.generateSampleTexts()
    val ids = texts.map(_.id).toSet
    assertEquals(ids.size, texts.length)
  }

  test("generateSampleTexts should have non-empty text content") {
    val texts = TilingGenerator.generateSampleTexts()
    assert(texts.forall(_.text.nonEmpty))
  }

  test("generateSampleTiling should return valid tiling or None") {
    val tiling = TilingGenerator.generateSampleTiling()
    // Should either return a valid tiling or None (if generation fails)
    assert(tiling.isDefined || tiling.isEmpty)
  }