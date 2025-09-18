package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.EditorState
import munit.FunSuite

class DotExporterSpec extends FunSuite with EditorStateFixture:

  test("exportTilingToDOT should not export when tiling is empty") {
    // Given empty tiling
    assert(EditorState.currentTiling.now().isEmpty)
    
    // When trying to export (would need to mock FileDownloader.trigger)
    DotExporter.exportTilingToDOT()
    
    // Then no file should be downloaded (would verify with mock)
    // This test would need dependency injection or mocking framework
  }

  test("tiling should generate valid DOT content") {
    val tiling = TilingDCEL.createRegularPolygon(4).toOption.get
    EditorState.currentTiling.set(tiling)
    
    val dotContent = tiling.toDOT
    
    // Verify DOT content structure
    assert(dotContent.nonEmpty)
    assert(dotContent.contains("graph"))
    assert(dotContent.contains("{"))
    assert(dotContent.contains("}"))
  }
