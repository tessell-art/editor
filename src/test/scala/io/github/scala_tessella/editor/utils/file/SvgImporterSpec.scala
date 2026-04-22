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
    EditorState.polygonColors.set(Map(faceId -> color))

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
    EditorState.polygonColors.set(Map.empty)
    EditorState.errorMessage.set(None)

    SvgImporter.importTilingFromSVG(svg, "strict.svg")

    val imported = EditorState.tessellationState.now().currentTiling
    assert(!imported.isEmpty)
    assertEquals(imported.toMetadata, tiling.toMetadata)
    assertEquals(EditorState.currentFileName.now(), Some("strict.svg"))
    assertEquals(EditorState.polygonColors.now().size, imported.innerFaces.size)
    assertEquals(EditorState.polygonColors.now().get(imported.innerFaces.head.id), Some(color))
    assertEquals(EditorState.errorMessage.now(), None)
  }

  test("importTilingFromSVG should fail when any polygon fill is invalid") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id

    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.polygonColors.set(Map(faceId -> ColorRGB(100, 120, 140)))

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
    EditorState.polygonColors.set(Map.empty)
    EditorState.errorMessage.set(None)
    EditorState.currentFileName.set(None)

    SvgImporter.importTilingFromSVG(invalidFillSvg, "invalid-fill.svg")

    assert(EditorState.tessellationState.now().currentTiling.isEmpty)
    assertEquals(EditorState.polygonColors.now(), Map.empty)
    assertEquals(EditorState.currentFileName.now(), None)
    assert(EditorState.errorMessage.now().exists(_.contains("Strict color import failed")))
    assert(EditorState.errorMessage.now().exists(_.contains("invalid fill")))
  }

  test("importTilingFromSVG should fail when polygon fill count does not match face count") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id

    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.polygonColors.set(Map(faceId -> ColorRGB(10, 20, 30)))

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
    EditorState.polygonColors.set(Map.empty)
    EditorState.errorMessage.set(None)

    SvgImporter.importTilingFromSVG(missingPolygonSvg, "missing-polygon.svg")

    assert(EditorState.tessellationState.now().currentTiling.isEmpty)
    assertEquals(EditorState.polygonColors.now(), Map.empty)
    assert(EditorState.errorMessage.now().exists(_.contains("Strict color import failed")))
    assert(EditorState.errorMessage.now().exists(_.contains("expected 1 polygon fills, found 0")))
  }
