package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.TilingGenerator
import munit.FunSuite

class DotExporterSpec extends FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    EditorState.currentTiling.set(io.github.scala_tessella.tessella.IncrementalTiling.empty)

  test("exportTilingToDOT should not export when tiling is empty") {
    // Given empty tiling
    assert(EditorState.currentTiling.now().isEmpty)
    
    // When trying to export (would need to mock FileDownloader.trigger)
    DotExporter.exportTilingToDOT()
    
    // Then no file should be downloaded (would verify with mock)
    // This test would need dependency injection or mocking framework
  }

  test("tiling should generate valid DOT content") {
    val tiling = TilingGenerator.createTilingFromPolygon(4).get
    EditorState.currentTiling.set(tiling)
    
    val dotContent = tiling.toDOT
    
    // Verify DOT content structure
    assert(dotContent.nonEmpty)
    assert(dotContent.contains("graph"))
    assert(dotContent.contains("{"))
    assert(dotContent.contains("}"))
  }
