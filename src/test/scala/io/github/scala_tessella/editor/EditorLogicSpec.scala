package io.github.scala_tessella.editor

import io.github.scala_tessella.dcel.FaceId
import munit.FunSuite
import io.github.scala_tessella.editor.models.{AppState, EditorState}
import io.github.scala_tessella.editor.models.ViewTransform
import io.github.scala_tessella.editor.operations.TessellationOperations.*
import io.github.scala_tessella.editor.operations.ErrorOperations.*
import io.github.scala_tessella.editor.operations.SelectionOperations.*

class EditorLogicSpec extends FunSuite with EditorStateFixture:

  test("Editor should start with empty tiling") {
    assert(AppState.isTilingEmpty)
    assert(EditorState.currentTiling.now().isEmpty)
  }

  test("Error message should be empty by default") {
    assert(EditorState.errorMessage.now().isEmpty)
  }

  test("Node labels should be hidden by default") {
    assert(!EditorState.showNodeLabels.now())
  }

  test("Toggle node labels should work") {
    // Initially hidden
    assert(!EditorState.showNodeLabels.now())

    // Toggle to show
    AppState.toggleNodeLabels()
    assert(EditorState.showNodeLabels.now())

    // Toggle to hide
    AppState.toggleNodeLabels()
    assert(!EditorState.showNodeLabels.now())
  }

  test("Polygon selection should create tiling when tiling is empty") {
    // Initially tiling should be empty
    assert(AppState.isTilingEmpty)

    // Select a triangle (3 sides)
    selectPolygon(3)

    // Now tiling should be created
    assert(!AppState.isTilingEmpty)
    assert(!EditorState.currentTiling.now().isEmpty)
    assertEquals(EditorState.selectedPolygon.now(), Some(3))
  }

  test("Polygon selection should not change existing tiling") {
    // Start with a tiling by selecting hexagon
    selectPolygon(6)
    val initialTiling = EditorState.currentTiling.now()
    assert(!initialTiling.isEmpty)

    // Select a different polygon - should not change tiling
    selectPolygon(4)
    val afterTiling = EditorState.currentTiling.now()

    // Tiling should be the same
    assertEquals(afterTiling, initialTiling)
    // But selection should change
    assertEquals(EditorState.selectedPolygon.now(), Some(4))
  }

  test("handlePerimeterEdgeClick should toggle selection when no polygon selected") {
    // Create a tiling first
    selectPolygon(6)
    assert(!AppState.isTilingEmpty)

    // Clear polygon selection
    EditorState.selectedPolygon.set(None)

    // Handle edge click
    val edgeId = "edge-1"
    AppState.handlePerimeterEdgeClick(edgeId, 0)

    // Should toggle selection (no error because we have a tiling, just no polygon selected)
    assert(EditorState.selectedPerimeterEdges.now().contains(edgeId))
  }

  test("Clear tiling should reset to empty state") {
    // Create a tiling
    selectPolygon(6)
    assert(!AppState.isTilingEmpty)

    // Clear tiling
    clearTiling()

    // Should be empty again
    assert(AppState.isTilingEmpty)
    assert(EditorState.currentTiling.now().isEmpty)
    assert(EditorState.selectedTilingPolygons.now().isEmpty)
    assert(EditorState.selectedPerimeterEdges.now().isEmpty)
  }

  test("Clear tiling should not affect node label visibility or error messages") {
    // Set node labels to visible and show error
    EditorState.showNodeLabels.set(true)
    showError("Test error")

    // Create and clear tiling
    selectPolygon(6)
    clearTiling()

    // Node labels visibility and error should remain unchanged
    assert(EditorState.showNodeLabels.now())
    assert(EditorState.errorMessage.now().isDefined)
  }

  test("Clear all selections should not affect node label visibility or error messages") {
    // Set node labels to visible and show error
    EditorState.showNodeLabels.set(true)
    showError("Test error")

    // Create a tiling and set up selections
    selectPolygon(6)
    EditorState.selectedTilingPolygons.set(Set(FaceId("F1")))
    clearAllSelections()

    // Node labels visibility and error should remain unchanged
    assert(EditorState.showNodeLabels.now())
    assert(EditorState.errorMessage.now().isDefined)
  }

  test("Polygon selection preserves existing tiling complexity") {
    // Start with a hexagon tiling
    selectPolygon(6)
    val initialTiling = EditorState.currentTiling.now()
    assert(!initialTiling.isEmpty)

    // Simulate growing the tiling by adding selections
    EditorState.selectedTilingPolygons.set(Set(FaceId("F1"), FaceId("F2")))
    EditorState.selectedPerimeterEdges.set(Set("edge1"))

    // Change polygon selection
    selectPolygon(4)

    // Tiling should remain the same
    assertEquals(EditorState.currentTiling.now(), initialTiling)
    // Selections should be preserved
    assertEquals(EditorState.selectedTilingPolygons.now(), Set(FaceId("F1"), FaceId("F2")))
    assertEquals(EditorState.selectedPerimeterEdges.now(), Set("edge1"))
    // Only the selected polygon type should change
    assertEquals(EditorState.selectedPolygon.now(), Some(4))
  }