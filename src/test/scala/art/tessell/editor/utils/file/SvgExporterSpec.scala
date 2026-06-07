package art.tessell.editor.utils.file

import art.tessell.editor.EditorStateFixture
import art.tessell.editor.models.{EditorConfig, EditorState}
import art.tessell.editor.utils.{ColorRGB, TilingBuilders}
import art.tessell.editor.utils.geo.Point
import io.github.scala_tessella.dcel.geometry.BigPoint
import io.github.scala_tessella.dcel.conversion.TilingSVG
import io.github.scala_tessella.dcel.conversion.TilingSVG.toMetadata
import io.github.scala_tessella.dcel.structure.VertexId
import io.github.scala_tessella.dcel.TilingDCEL
import art.tessell.editor.utils.geo.Geometry.fitPointsToViewBox
import art.tessell.editor.utils.geo.TessellationGeometry.toPoint
import munit.FunSuite

class SvgExporterSpec extends FunSuite with EditorStateFixture:

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

  private def defaultSvgContent(tiling: TilingDCEL = squareTiling): String =
    SvgExporter.generateSvgContent(
      tiling,
      showNodeLabels = false,
      showUniformity = false,
      showRotation = false,
      showReflection = false
    )

  // --- pointsString ------------------------------------------------------

  test("pointsString converts nodes to 'x,y x,y' with scale and offset") {
    val nodes  = List(node1, node2, node3)
    val result = SvgExporter.pointsString(nodes, testCoordinates, scale = 2.0, Point(5.0, 10.0))
    // node1: (0*2+5, 0*2+10) = (5, 10); node2: (1*2+5, 10); node3: (1*2+5, 1*2+10)
    assertEquals(result, "5.000000,10.000000 7.000000,10.000000 7.000000,12.000000")
  }

  test("pointsString handles empty and singleton inputs") {
    assertEquals(SvgExporter.pointsString(List.empty, testCoordinates, 1.0, Point.origin), "")
    assertEquals(
      SvgExporter.pointsString(List(node1), testCoordinates, 1.0, Point.origin),
      "0.000000,0.000000"
    )
  }

  // --- generatePolygonsXml ----------------------------------------------

  test("generatePolygonsXml produces <g> with correct polygon markup and points") {
    val result = SvgExporter.generatePolygonsXml(squareTiling, 1.0, Point.origin, 1.5)

    assert(result.contains("<g id=\"tiling-polygons\""))
    assert(result.contains("</g>"))
    assert(result.contains("polygon"))
    assert(result.contains("data-nodes="))
    assert(result.contains("fill="))
    assert(result.contains("stroke=\"#333\""))
    assert(result.contains("stroke-width=\"1.5\""))
    assert(result.contains(
      "points=\"0.000000,0.000000 1.000000,0.000000 1.000000,1.000000 0.000000,1.000000\""
    ))
  }

  test("generatePolygonsXml uses assigned color when a face has one") {
    val faceId = squareTiling.innerFaces.head.id
    val color  = ColorRGB(12, 34, 56)
    EditorState.colorState.update(_.copy(polygonColors = Map(faceId -> color)))

    val result = SvgExporter.generatePolygonsXml(squareTiling, 1.0, Point.origin, 1.5)
    assert(result.contains(s"""fill="${color.toRgb}""""))
  }

  test("generatePolygonsXml falls back to default color when none is assigned") {
    EditorState.colorState.update(_.copy(polygonColors = Map.empty))
    val result = SvgExporter.generatePolygonsXml(squareTiling, 1.0, Point.origin, 1.5)
    assert(result.contains(s"""fill="${EditorConfig.defaultPolygonColor.toRgb}""""))
  }

  // --- generatePerimeterXml ---------------------------------------------

  test("generatePerimeterXml produces polygon markup with perimeter styling") {
    val result = SvgExporter.generatePerimeterXml(squareTiling, 1.0, Point.origin, 10.5)

    assert(result.contains("polygon"))
    assert(result.contains("data-nodes="))
    assert(result.contains("points="))
    assert(result.contains("fill=\"none\""))
    assert(result.contains("stroke=\"#e4e4e4\""))
    assert(result.contains("stroke-width=\"10.5\""))
  }

  test("generatePerimeterXml returns empty string for empty tiling") {
    assertEquals(SvgExporter.generatePerimeterXml(TilingDCEL.empty, 1.0, Point.origin, 10.5), "")
  }

  // --- generateLabelsXml ------------------------------------------------

  test("generateLabelsXml emits styled <text> for every coordinate") {
    val result = SvgExporter.generateLabelsXml(testCoordinates, 1.0, Point.origin)

    // styling
    assert(result.contains("text"))
    assert(result.contains("font-family=\"monospace\""))
    assert(result.contains("font-weight=\"bold\""))
    assert(result.contains("font-size=\"12\""))
    assert(result.contains("fill=\"#000\""))
    assert(result.contains("stroke=\"#fff\""))
    assert(result.contains("stroke-width=\"0.5\""))
    // one label per vertex
    assert(result.contains(">1<"))
    assert(result.contains(">2<"))
    assert(result.contains(">3<"))
    assert(result.contains(">4<"))
  }

  test("generateLabelsXml positions labels with scale and offset") {
    val result = SvgExporter.generateLabelsXml(testCoordinates, 2.0, Point(5.0, 10.0))

    // node1 at (0,0): labelX = 0*2+5+4 = 9, labelY = 0*2+10-4 = 6
    assert(result.contains("x=\"9.0000\" y=\"6.0000\""))
    // node2 at (1,0): labelX = 1*2+5+4 = 11, labelY = 0*2+10-4 = 6
    assert(result.contains("x=\"11.0000\" y=\"6.0000\""))
  }

  test("generateLabelsXml returns empty string for empty coordinates") {
    assertEquals(SvgExporter.generateLabelsXml(Map.empty, 1.0, Point.origin), "")
  }

  // --- generateMetadataXml ----------------------------------------------

  test("generateMetadataXml emits RDF metadata with source, license, and coordinates") {
    val result = SvgExporter.generateMetadataXml(squareTiling)

    // RDF wrapper
    assert(result.contains("<metadata>"))
    assert(result.contains("</metadata>"))
    assert(result.contains("<rdf:RDF>"))
    assert(result.contains("</rdf:RDF>"))
    assert(result.contains("<cc:Work>"))
    assert(result.contains("</cc:Work>"))
    // source + license
    assert(result.contains("https://github.com/scala-tessella/tessella"))
    assert(result.contains("https://www.apache.org/licenses/LICENSE-2.0"))
    // tessella-dcel payload with vertices
    assert(result.contains(
      "<tessella:tessella-dcel xmlns:tessella=\"https://github.com/scala-tessella/tessella\"><vertices>"
    ))
    assert(result.contains("</vertices>"))
    assert(result.contains("<vertex id=\"V1\" x=\"0\" y=\"0\""))
  }

  test("generateMetadataXml on empty tiling emits wrapper without coordinates") {
    val result = SvgExporter.generateMetadataXml(TilingDCEL.empty)

    assert(result.contains("<metadata>"))
    assert(result.contains("</metadata>"))
    assert(!result.contains("<tess:tilingCoordinates>"))
  }

  // --- generateSvgContent -----------------------------------------------

  test("generateSvgContent returns empty string for empty tiling") {
    assertEquals(defaultSvgContent(TilingDCEL.empty), "")
  }

  test("generateSvgContent produces SVG envelope with dimensions and white background") {
    val result = defaultSvgContent()

    // envelope
    assert(result.contains("<svg"))
    assert(result.contains("</svg>"))
    assert(result.contains("xmlns=\"http://www.w3.org/2000/svg\""))
    assert(result.contains("xmlns:tess=\"https://github.com/scala-tessella/tessella\""))
    // dimensions
    assert(result.contains("width=\"90.0"))
    assert(result.contains("height=\"90.0"))
    // background
    assert(result.contains("<rect width=\"100%\" height=\"100%\" fill=\"white\"/>"))
  }

  test("generateSvgContent includes <text> labels iff showNodeLabels is true") {
    val withLabels = SvgExporter.generateSvgContent(
      squareTiling,
      showNodeLabels = true,
      showUniformity = false,
      showRotation = false,
      showReflection = false
    )
    assert(withLabels.contains("<text"))
    assert(withLabels.contains(">1<"))

    assert(!defaultSvgContent().contains("<text"))
  }

  test("generateSvgContent does not include dual tessellation when showUniformity is false") {
    assert(!defaultSvgContent().contains("<g id=\"dual-tessellation\""))
  }

  test("generateRotationXml returns empty string when rotation data is missing") {
    EditorState.viewState.update(_.copy(rotationVertexIds = None))
    assertEquals(SvgExporter.generateRotationXml(testCoordinates, 1.0, Point.origin), "")
  }

  test("generateSvgContent does not fail when showRotation is true but rotation data is missing") {
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

  test("generateSvgContent round-trips metadata back into an equivalent tiling") {
    val svgContent    = defaultSvgContent()
    val metadataRegex = "(?s)<tessella:tessella-dcel[^>]*>.*?</tessella:tessella-dcel>".r
    val metadata      = metadataRegex.findFirstIn(svgContent).getOrElse("")
    assert(metadata.nonEmpty)

    val parsed = TilingSVG.fromMetadata(metadata).toOption.get
    // Equality is not guaranteed on TilingDCEL, so compare canonical metadata instead.
    assertEquals(parsed.toMetadata, squareTiling.toMetadata)
  }

  test("generateSvgContent exports every face's assigned polygon fill color") {
    val faces  = squareTiling.innerFaces
    val colors = faces.zipWithIndex.map { (face, idx) =>

      face.id -> ColorRGB(10 + idx, 20 + idx, 30 + idx)
    }.toMap
    EditorState.colorState.update(_.copy(polygonColors = colors))

    val svgContent = defaultSvgContent()
    val groupRegex = "(?s)<g id=\"tiling-polygons\"[^>]*>(.*?)</g>".r
    val groupBody  = groupRegex.findFirstMatchIn(svgContent).map(_.group(1)).getOrElse("")
    assert(groupBody.nonEmpty)

    val fills = "fill=\"(rgb\\([^)]+\\))\"".r
      .findAllMatchIn(groupBody)
      .map(_.group(1))
      .toSet

    assertEquals(fills, colors.values.map(_.toRgb).toSet)
  }

  test("generateSvgContent snapshot includes canonical metadata and key elements") {
    val svgContent = defaultSvgContent()

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
