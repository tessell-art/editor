package io.github.scala_tessella.editor.utils.file

import io.github.scala_tessella.dcel.conversion.TilingSVG.toMetadata
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.{ColorRGB, TilingBuilders}
import munit.FunSuite

class SvgImporterSpec extends FunSuite with EditorStateFixture:

  test("importTilingFromSVG should preserve polygon colors strictly when SVG is valid") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id
    val color  = ColorRGB(12, 34, 56)

    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.colorState.update(_.copy(polygonColors = Map(faceId -> color)))

    val svg = SvgExporter.generateSvgContent(
      tiling,
      showNodeLabels = false,
      showUniformity = false,
      showRotation = false,
      showReflection = false
    )

    EditorState.tessellationState.update(_.copy(currentTiling =
      io.github.scala_tessella.dcel.TilingDCEL.empty
    ))
    EditorState.colorState.update(_.copy(polygonColors = Map.empty))
    EditorState.errorState.update(_.copy(errorMessage = None))

    SvgImporter.importTilingFromSVG(svg, "strict.svg")

    val imported = EditorState.tessellationState.now().currentTiling
    assert(!imported.isEmpty)
    assertEquals(imported.toMetadata, tiling.toMetadata)
    assertEquals(EditorState.fileState.now().currentFileName, Some("strict.svg"))
    assertEquals(EditorState.colorState.now().polygonColors.size, imported.innerFaces.size)
    assertEquals(EditorState.colorState.now().polygonColors.get(imported.innerFaces.head.id), Some(color))
    assertEquals(EditorState.errorState.now().errorMessage, None)
  }

  test("importTilingFromSVG should fail when any polygon fill is invalid") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id

    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.colorState.update(_.copy(polygonColors = Map(faceId -> ColorRGB(100, 120, 140))))

    val svg            = SvgExporter.generateSvgContent(
      tiling,
      showNodeLabels = false,
      showUniformity = false,
      showRotation = false,
      showReflection = false
    )
    val invalidFillSvg = svg.replaceFirst("""fill="rgb\([^)]+\)"""", """fill="not-a-color"""")

    EditorState.tessellationState.update(_.copy(currentTiling =
      io.github.scala_tessella.dcel.TilingDCEL.empty
    ))
    EditorState.colorState.update(_.copy(polygonColors = Map.empty))
    EditorState.errorState.update(_.copy(errorMessage = None))
    EditorState.fileState.update(_.copy(currentFileName = None))

    SvgImporter.importTilingFromSVG(invalidFillSvg, "invalid-fill.svg")

    assert(EditorState.tessellationState.now().currentTiling.isEmpty)
    assertEquals(EditorState.colorState.now().polygonColors, Map.empty)
    assertEquals(EditorState.fileState.now().currentFileName, None)
    assert(EditorState.errorState.now().errorMessage.exists(_.contains("Strict color import failed")))
    assert(EditorState.errorState.now().errorMessage.exists(_.contains("invalid fill")))
  }

  test("importTilingFromSVG should fail when polygon fill count does not match face count") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id

    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.colorState.update(_.copy(polygonColors = Map(faceId -> ColorRGB(10, 20, 30))))

    val svg               = SvgExporter.generateSvgContent(
      tiling,
      showNodeLabels = false,
      showUniformity = false,
      showRotation = false,
      showReflection = false
    )
    val missingPolygonSvg =
      svg.replaceFirst("""(?s)(<g id="tiling-polygons"[^>]*>)(.*?)(</g>)""", "$1$3")

    EditorState.tessellationState.update(_.copy(currentTiling =
      io.github.scala_tessella.dcel.TilingDCEL.empty
    ))
    EditorState.colorState.update(_.copy(polygonColors = Map.empty))
    EditorState.errorState.update(_.copy(errorMessage = None))

    SvgImporter.importTilingFromSVG(missingPolygonSvg, "missing-polygon.svg")

    assert(EditorState.tessellationState.now().currentTiling.isEmpty)
    assertEquals(EditorState.colorState.now().polygonColors, Map.empty)
    assert(EditorState.errorState.now().errorMessage.exists(_.contains("Strict color import failed")))
    assert(EditorState.errorState.now().errorMessage.exists(_.contains("expected 1 polygon fills, found 0")))
  }
