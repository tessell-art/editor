package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.dcel.BigDecimalGeometry.BigPoint
import io.github.scala_tessella.dcel.{TilingDCEL, VertexId}
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.Geometry.Point
import munit.FunSuite

class SvgExporterSpec extends FunSuite with EditorStateFixture:

  // Test data setup
  private val node1 = VertexId("V1")
  private val node2 = VertexId("V2")
  private val node3 = VertexId("V3")
  private val node4 = VertexId("V4")

  private val testCoordinates: Map[VertexId, BigPoint] = Map(
    node1 -> BigPoint(0, 0),
    node2 -> BigPoint(1, 0),
    node3 -> BigPoint(1, 1),
    node4 -> BigPoint(0, 1)
  )

  private val squareTiling = TilingBuilders.freshSquare()

  test("should convert nodes to SVG points string") {
    val nodes  = List(node1, node2, node3)
    val scale  = 2.0
    val offset = Point(5.0, 10.0)
    val result = SvgExporter.pointsString(nodes, testCoordinates, scale, offset)

    // node1: (0*2+5, 0*2+10) = (5, 10)
    val expected = "5.000000,10.000000 7.000000,10.000000 7.000000,12.000000"
    assertEquals(result, expected)
  }

  test("should handle empty nodes list") {
    val result = SvgExporter.pointsString(List.empty, testCoordinates, 1.0, Point.origin)
    assertEquals(result, "")
  }

  test("should handle single node") {
    val result = SvgExporter.pointsString(List(node1), testCoordinates, 1.0, Point.origin)
    assertEquals(result, "0.000000,0.000000")
  }

  test("should generate polygons XML with correct structure") {
    val result = SvgExporter.generatePolygonsXml(squareTiling, 1.0, Point.origin, 1.5)

    assert(result.contains("<g id=\"tiling-polygons\""))
    assert(result.contains("</g>"))
    assert(result.contains("polygon"))
    assert(result.contains("data-nodes="))
    assert(result.contains("points="))
    assert(result.contains("fill="))
    assert(result.contains("stroke=\"#333\""))
    assert(result.contains("stroke-width=\"1.5\""))
  }

  test("should include correct points in polygon") {
    val result = SvgExporter.generatePolygonsXml(squareTiling, 1.0, Point.origin, 1.5)
    // Should contain the points from our test coordinates
    assert(
      result.contains("points=\"0.000000,0.000000 1.000000,0.000000 1.000000,1.000000 0.000000,1.000000\"")
    )
  }

  test("should include node data attribute") {
    val result = SvgExporter.generatePolygonsXml(squareTiling, 1.0, Point.origin, 1.5)

    // Should contain nodes in the data attribute
    assert(result.contains("data-nodes=\"\""))
  }

  test("should generate perimeter XML when perimeter exists") {
    val result = SvgExporter.generatePerimeterXml(squareTiling, 1.0, Point.origin, 10.5)

    assert(result.contains("polygon"))
    assert(result.contains("data-nodes="))
    assert(result.contains("points="))
    assert(result.contains("fill=\"none\""))
    assert(result.contains("stroke=\"#e4e4e4\""))
    assert(result.contains("stroke-width=\"10.5\""))
  }

  test("should return empty string when no perimeter") {
    val emptyTiling = TilingDCEL.empty

    val result = SvgExporter.generatePerimeterXml(emptyTiling, 1.0, Point.origin, 10.5)
    assertEquals(result, "")
  }

//  test("should generate dual tessellation XML for a valid tiling") {
//    val result = SvgExporter.generateDualTessellationXml(squareTiling, 1.0, 0.0, 0.0)
//    assert(result.contains("<g id=\"dual-tessellation\""))
//    assert(result.contains("<line"))
//    assert(result.contains("stroke=\"red\""))
//    assert(result.contains("stroke-width=\"1\""))
//  }
//
//  test("should return empty string for dual tessellation on empty tiling") {
//    val result = SvgExporter.generateDualTessellationXml(TilingDCEL.empty, 1.0, 0.0, 0.0)
//    assertEquals(result, "")
//  }

  test("should generate labels XML for all coordinates") {
    val result = SvgExporter.generateLabelsXml(testCoordinates, 1.0, Point.origin)

    assert(result.contains("text"))
    assert(result.contains("font-family=\"monospace\""))
    assert(result.contains("font-weight=\"bold\""))
    assert(result.contains("font-size=\"12\""))
    assert(result.contains("fill=\"#000\""))
    assert(result.contains("stroke=\"#fff\""))
    assert(result.contains("stroke-width=\"0.5\""))
  }

  test("should position labels correctly with offset") {
    val result = SvgExporter.generateLabelsXml(testCoordinates, 2.0, Point(5.0, 10.0))

    // For node1 at (0,0): labelX = 0*2+5+4 = 9, labelY = 0*2+10-4 = 6
    assert(result.contains("x=\"9.0000\" y=\"6.0000\""))
    assert(result.contains("x=\"11.0000\" y=\"6.0000\""))
  }

  test("should include node text content") {
    val result = SvgExporter.generateLabelsXml(testCoordinates, 1.0, Point.origin)

    assert(result.contains(">V1<"))
    assert(result.contains(">V2<"))
    assert(result.contains(">V3<"))
    assert(result.contains(">V4<"))
  }

  test("should handle empty coordinates") {
    val result = SvgExporter.generateLabelsXml(Map.empty, 1.0, Point.origin)
    assertEquals(result, "")
  }

  test("should generate metadata XML with RDF structure") {
    val result = SvgExporter.generateMetadataXml(squareTiling)

    assert(result.contains("<metadata>"))
    assert(result.contains("</metadata>"))
    assert(result.contains("<rdf:RDF>"))
    assert(result.contains("</rdf:RDF>"))
    assert(result.contains("<cc:Work>"))
    assert(result.contains("</cc:Work>"))
  }

  test("should include Tessella source and license") {
    val result = SvgExporter.generateMetadataXml(squareTiling)

    assert(result.contains("https://github.com/scala-tessella/tessella"))
    assert(result.contains("https://www.apache.org/licenses/LICENSE-2.0"))
  }

  test("should include tiling coordinates in metadata") {
    val result = SvgExporter.generateMetadataXml(squareTiling)

    assert(result.contains(
      "<tessella:tessella-dcel xmlns:tessella=\"https://github.com/scala-tessella/tessella\"><vertices>"
    ))
    assert(result.contains("</vertices>"))
    assert(result.contains("<vertex id=\"V1\" x=\"0\" y=\"0\""))
  }

  test("should handle empty tiling without coordinates") {
    val result = SvgExporter.generateMetadataXml(TilingDCEL.empty)

    assert(result.contains("<metadata>"))
    assert(result.contains("</metadata>"))
    assert(!result.contains("<tess:tilingCoordinates>"))
  }

  test("should return empty string for empty tiling") {
    val emptyTiling = TilingDCEL.empty

    val result = SvgExporter.generateSvgContent(emptyTiling, showNodeLabels = false, showDual = false)
    assertEquals(result, "")
  }

  test("should generate complete SVG structure") {
    val result = SvgExporter.generateSvgContent(squareTiling, showNodeLabels = false, showDual = false)

    assert(result.contains("<svg"))
    assert(result.contains("</svg>"))
    assert(result.contains("xmlns=\"http://www.w3.org/2000/svg\""))
    assert(result.contains("xmlns:tess=\"https://github.com/scala-tessella/tessella\""))
    assert(result.contains("width="))
    assert(result.contains("height="))
  }

  test("should include white background") {
    val result = SvgExporter.generateSvgContent(squareTiling, showNodeLabels = false, showDual = false)

    assert(result.contains("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>"))
  }

  test("should include labels when showNodeLabels is true") {
    val result = SvgExporter.generateSvgContent(squareTiling, showNodeLabels = true, showDual = false)
    EditorState.showNodeLabels.set(true)
    assert(result.contains("<text"))
    assert(result.contains(">V1<"))
  }

  test("should not include labels when showNodeLabels is false") {
    val result = SvgExporter.generateSvgContent(squareTiling, showNodeLabels = false, showDual = false)
    EditorState.showNodeLabels.set(false)
    assert(!result.contains("<text"))
  }

//  test("should include dual tessellation when showDual is true") {
//    val result = SvgExporter.generateSvgContent(squareTiling, showNodeLabels = false, showDual = true)
//    EditorState.showDual.set(true)
//    assert(result.contains("<g id=\"dual-tessellation\""))
//  }

  test("should not include dual tessellation when showDual is false") {
    val result = SvgExporter.generateSvgContent(squareTiling, showNodeLabels = false, showDual = false)
    EditorState.showDual.set(false)
    assert(!result.contains("<g id=\"dual-tessellation\""))
  }

  test("should calculate correct dimensions and offsets") {
    val result = SvgExporter.generateSvgContent(squareTiling, showNodeLabels = false, showDual = false)
    assert(result.contains("width=\"90.0"))
    assert(result.contains("height=\"90.0"))
  }
