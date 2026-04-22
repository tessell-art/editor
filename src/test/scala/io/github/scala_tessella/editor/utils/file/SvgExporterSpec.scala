package io.github.scala_tessella.editor.utils.file

import io.github.scala_tessella.dcel.geometry.BigPoint
import io.github.scala_tessella.dcel.conversion.TilingSVG
import io.github.scala_tessella.dcel.conversion.TilingSVG.toMetadata
import io.github.scala_tessella.dcel.structure.VertexId
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.{EditorConfig, EditorState}
import io.github.scala_tessella.editor.utils.ColorRGB
import io.github.scala_tessella.editor.utils.geo.Geometry.fitPointsToViewBox
import io.github.scala_tessella.editor.utils.geo.Point
import io.github.scala_tessella.editor.utils.geo.TessellationGeometry.toPoint
import io.github.scala_tessella.editor.utils.TilingBuilders
import munit.FunSuite

class SvgExporterSpec extends FunSuite with EditorStateFixture:

  // Test data setup
  private val node1 = VertexId(1)
  private val node2 = VertexId(2)
  private val node3 = VertexId(3)
  private val node4 = VertexId(4)

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

  test("should use assigned polygon colors when exporting polygons") {
    val faceId = squareTiling.innerFaces.head.id
    val color  = ColorRGB(12, 34, 56)
    EditorState.colorState.update(_.copy(polygonColors = Map(faceId -> color)))

    val result = SvgExporter.generatePolygonsXml(squareTiling, 1.0, Point.origin, 1.5)
    assert(result.contains(s"""fill="${color.toRgb}""""))
  }

  test("should fall back to default polygon color when none is assigned") {
    EditorState.colorState.update(_.copy(polygonColors = Map.empty))
    val result = SvgExporter.generatePolygonsXml(squareTiling, 1.0, Point.origin, 1.5)
    assert(result.contains(s"""fill="${EditorConfig.defaultPolygonColor.toRgb}""""))
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

    assert(result.contains(">1<"))
    assert(result.contains(">2<"))
    assert(result.contains(">3<"))
    assert(result.contains(">4<"))
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

    val result = SvgExporter.generateSvgContent(
      emptyTiling,
      showNodeLabels = false,
      showUniformity = false,
      showRotation = false,
      showReflection = false
    )
    assertEquals(result, "")
  }

  test("should generate complete SVG structure") {
    val result = SvgExporter.generateSvgContent(
      squareTiling,
      showNodeLabels = false,
      showUniformity = false,
      showRotation = false,
      showReflection = false
    )

    assert(result.contains("<svg"))
    assert(result.contains("</svg>"))
    assert(result.contains("xmlns=\"http://www.w3.org/2000/svg\""))
    assert(result.contains("xmlns:tess=\"https://github.com/scala-tessella/tessella\""))
    assert(result.contains("width="))
    assert(result.contains("height="))
  }

  test("should include white background") {
    val result = SvgExporter.generateSvgContent(
      squareTiling,
      showNodeLabels = false,
      showUniformity = false,
      showRotation = false,
      showReflection = false
    )

    assert(result.contains("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>"))
  }

  test("should include labels when showNodeLabels is true") {
    val result = SvgExporter.generateSvgContent(
      squareTiling,
      showNodeLabels = true,
      showUniformity = false,
      showRotation = false,
      showReflection = false
    )
    EditorState.viewState.update(_.copy(showNodeLabels = true))
    assert(result.contains("<text"))
    assert(result.contains(">1<"))
  }

  test("should not include labels when showNodeLabels is false") {
    val result = SvgExporter.generateSvgContent(
      squareTiling,
      showNodeLabels = false,
      showUniformity = false,
      showRotation = false,
      showReflection = false
    )
    EditorState.viewState.update(_.copy(showNodeLabels = false))
    assert(!result.contains("<text"))
  }

//  test("should include dual tessellation when showDual is true") {
//    val result = SvgExporter.generateSvgContent(squareTiling, showNodeLabels = false, showDual = true)
//    EditorState.showDual.set(true)
//    assert(result.contains("<g id=\"dual-tessellation\""))
//  }

  test("should not include dual tessellation when showDual is false") {
    val result = SvgExporter.generateSvgContent(
      squareTiling,
      showNodeLabels = false,
      showUniformity = false,
      showRotation = false,
      showReflection = false
    )
    EditorState.viewState.update(_.copy(showUniformity = false))
    assert(!result.contains("<g id=\"dual-tessellation\""))
  }

  test("generateRotationXml should return empty string when rotation data is missing") {
    EditorState.viewState.update(_.copy(rotationVertexIds = None))

    val result = SvgExporter.generateRotationXml(testCoordinates, 1.0, Point.origin)
    assertEquals(result, "")
  }

  test("generateSvgContent should not fail when showRotation is true but rotation data is missing") {
    EditorState.viewState.update(_.copy(rotationVertexIds = None))

    val result = SvgExporter.generateSvgContent(
      squareTiling,
      showNodeLabels = false,
      showUniformity = false,
      showRotation = true,
      showReflection = false
    )

    assert(result.contains("<svg"))
    assert(!result.contains("stroke=\"Gold\""))
  }

  test("should calculate correct dimensions and offsets") {
    val result = SvgExporter.generateSvgContent(
      squareTiling,
      showNodeLabels = false,
      showUniformity = false,
      showRotation = false,
      showReflection = false
    )
    assert(result.contains("width=\"90.0"))
    assert(result.contains("height=\"90.0"))
  }

  test("generateSvgContent should round-trip metadata into a tiling") {
    val svgContent = SvgExporter.generateSvgContent(
      squareTiling,
      showNodeLabels = false,
      showUniformity = false,
      showRotation = false,
      showReflection = false
    )

    val metadataRegex =
      "(?s)<tessella:tessella-dcel[^>]*>.*?</tessella:tessella-dcel>".r
    val metadata      = metadataRegex.findFirstIn(svgContent).getOrElse("")
    assert(metadata.nonEmpty)

    val parsed = TilingSVG.fromMetadata(metadata).toOption.get
    // Equality is not guaranteed on TilingDCEL, so compare canonical metadata instead.
    assertEquals(parsed.toMetadata, squareTiling.toMetadata)
  }

  test("generateSvgContent should export polygon fill colors for all faces") {
    val faces  = squareTiling.innerFaces
    val colors = faces.zipWithIndex.map { (face, idx) =>

      face.id -> ColorRGB(10 + idx, 20 + idx, 30 + idx)
    }.toMap
    EditorState.colorState.update(_.copy(polygonColors = colors))

    val svgContent = SvgExporter.generateSvgContent(
      squareTiling,
      showNodeLabels = false,
      showUniformity = false,
      showRotation = false,
      showReflection = false
    )

    val groupRegex = "(?s)<g id=\"tiling-polygons\"[^>]*>(.*?)</g>".r
    val groupBody  = groupRegex.findFirstMatchIn(svgContent).map(_.group(1)).getOrElse("")
    assert(groupBody.nonEmpty)

    val fills = "fill=\"(rgb\\([^)]+\\))\"".r
      .findAllMatchIn(groupBody)
      .map(_.group(1))
      .toSet

    val expected = colors.values.map(_.toRgb).toSet
    assertEquals(fills, expected)
  }

  test("generateSvgContent snapshot includes canonical metadata and key elements") {
    val svgContent = SvgExporter.generateSvgContent(
      squareTiling,
      showNodeLabels = false,
      showUniformity = false,
      showRotation = false,
      showReflection = false
    )

    def normalize(s: String): String =
      s.replaceAll("\\s+", "")

    val svgTagRegex                = "(?s)<svg\\s+([^>]+)>".r
    val svgAttrs                   = svgTagRegex.findFirstMatchIn(svgContent).map(_.group(1)).getOrElse("")
    def attr(name: String): String =
      s"""$name="([^"]+)"""".r.findFirstMatchIn(svgAttrs).map(_.group(1)).getOrElse("")

    val metadataRegex =
      "(?s)<tessella:tessella-dcel[^>]*>.*?</tessella:tessella-dcel>".r
    val metadata      = metadataRegex.findFirstIn(svgContent).getOrElse("")

    val polygonGroupRegex = "(?s)<g id=\"tiling-polygons\"[^>]*>(.*?)</g>".r
    val groupBody         = polygonGroupRegex.findFirstMatchIn(svgContent).map(_.group(1)).getOrElse("")
    val polygonPoints     = "points=\"([^\"]+)\"".r.findFirstMatchIn(groupBody).map(_.group(1)).getOrElse("")
    val polygonCount      = "<polygon".r.findAllMatchIn(groupBody).size

    val coordinates           = squareTiling.coordinates.values.toList.map(_.toPoint)
    val (w, h, offset)        = coordinates.fitPointsToViewBox(EditorConfig.canvasScale, padding = 20.0)
    val expectedWidth         = w
    val expectedHeight        = h
    val expectedPolygonsGroup =
      SvgExporter.generatePolygonsXml(squareTiling, EditorConfig.canvasScale, offset, strokeWidth = 1.5)
    val expectedGroupBody     =
      polygonGroupRegex.findFirstMatchIn(expectedPolygonsGroup).map(_.group(1)).getOrElse("")
    val expectedFirstPoints   =
      "points=\"([^\"]+)\"".r.findFirstMatchIn(expectedGroupBody).map(_.group(1)).getOrElse("")

    assertEquals(polygonCount, 1)
    assertEquals(polygonPoints, expectedFirstPoints)
    assertEquals(normalize(metadata), normalize(squareTiling.toMetadata))
    assertEqualsDouble(attr("width").toDouble, expectedWidth, 1e-6)
    assertEqualsDouble(attr("height").toDouble, expectedHeight, 1e-6)
  }

  private def assertEqualsDouble(obtained: Double, expected: Double, delta: Double): Unit =
    assert(
      math.abs(obtained - expected) <= delta,
      s"obtained=$obtained expected=$expected delta=$delta"
    )
