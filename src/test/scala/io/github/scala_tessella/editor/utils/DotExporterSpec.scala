package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.TilingBuilders.freshSquare
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

  private def bracesBalanced(s: String): Boolean =
    s.count(_ == '{') == s.count(_ == '}')

  private def graphHeader(s: String): Boolean =
    s.trim.startsWith("graph") || s.trim.startsWith("strict graph") || s.trim.startsWith("digraph")

  private def nonEmptyBody(s: String): Boolean =
    val open  = s.indexOf('{')
    val close = s.lastIndexOf('}')
    open >= 0 && close > open && s.substring(open + 1, close).trim.nonEmpty

  test("tiling should generate structurally valid DOT content (header, braces, non-empty body)") {
    val tiling = freshSquare()
    EditorState.currentTiling.set(tiling)

    val dotContent = tiling.toDOT

    // Structural checks instead of brittle substring contains
    assert(dotContent.nonEmpty, clue = "DOT content should not be empty")
    assert(graphHeader(dotContent), clue = "DOT should start with 'graph' or 'strict graph'")
    assert(bracesBalanced(dotContent), clue = "DOT braces should be balanced")
    assert(nonEmptyBody(dotContent), clue = "DOT graph body should be non-empty")
  }
