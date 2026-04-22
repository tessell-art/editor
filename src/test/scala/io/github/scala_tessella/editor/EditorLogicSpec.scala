package io.github.scala_tessella.editor

import io.github.scala_tessella.dcel.structure.FaceId
import io.github.scala_tessella.editor.AppState
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.operations.ErrorOperations.*
import io.github.scala_tessella.editor.operations.SelectionOperations.*
import io.github.scala_tessella.editor.operations.TessellationOperations.*
import munit.FunSuite

class EditorLogicSpec extends FunSuite with EditorStateFixture:

  test("Editor should start with empty tiling") {
    assert(AppState.isTilingEmpty)
    assert(EditorState.tessellationState.now().currentTiling.isEmpty)
  }

  test("Error message should be empty by default") {
    assert(EditorState.errorState.now().errorMessage.isEmpty)
  }

  test("Node labels should be hidden by default") {
    assert(!EditorState.viewState.now().showNodeLabels)
  }

  test("Toggle node labels should work") {
    // Initially hidden
    assert(!EditorState.viewState.now().showNodeLabels)

    // Toggle to show
    AppState.toggleNodeLabels()
    assert(EditorState.viewState.now().showNodeLabels)

    // Toggle to hide
    AppState.toggleNodeLabels()
    assert(!EditorState.viewState.now().showNodeLabels)
  }

  test("Polygon selection should create tiling when tiling is empty") {
    // Initially tiling should be empty
    assert(AppState.isTilingEmpty)

    // Select a triangle (3 sides)
    selectPolygon(3)

    // Now tiling should be created
    assert(!AppState.isTilingEmpty)
    assert(!EditorState.tessellationState.now().currentTiling.isEmpty)
    assertEquals(EditorState.toolState.now().selectedPolygon, Some(3))
  }

  test("Polygon selection should not change existing tiling") {
    // Start with a tiling by selecting hexagon
    selectPolygon(6)
    val initialTiling = EditorState.tessellationState.now().currentTiling
    assert(!initialTiling.isEmpty)

    // Select a different polygon - should not change tiling
    selectPolygon(4)
    val afterTiling = EditorState.tessellationState.now().currentTiling

    // Tiling should be the same
    assertEquals(afterTiling, initialTiling)
    // But selection should change
    assertEquals(EditorState.toolState.now().selectedPolygon, Some(4))
  }

  test("handlePerimeterEdgeClick should toggle selection when no polygon selected") {
    // Create a tiling first
    selectPolygon(6)
    assert(!AppState.isTilingEmpty)

    // Clear polygon selection
    EditorState.toolState.update(_.copy(selectedPolygon = None))

    // Handle edge click
    val edgeId = "edge-1"
    AppState.handlePerimeterEdgeClick(edgeId, 0)

    // Should toggle selection (no error because we have a tiling, just no polygon selected)
    assert(EditorState.tessellationState.now().selectedPerimeterEdges.contains(edgeId))
  }

  test("Clear tiling should reset to empty state") {
    // Create a tiling
    selectPolygon(6)
    assert(!AppState.isTilingEmpty)

    // Clear tiling
    clearTiling()

    // Should be empty again
    assert(AppState.isTilingEmpty)
    assert(EditorState.tessellationState.now().currentTiling.isEmpty)
    assert(EditorState.tessellationState.now().selectedTilingPolygons.isEmpty)
    assert(EditorState.tessellationState.now().selectedPerimeterEdges.isEmpty)
  }

  test("Clear tiling should not affect node label visibility or error messages") {
    // Set node labels to visible and show error
    EditorState.viewState.update(_.copy(showNodeLabels = true))
    showError("Test error")

    // Create and clear tiling
    selectPolygon(6)
    clearTiling()

    // Node labels visibility and error should remain unchanged
    assert(EditorState.viewState.now().showNodeLabels)
    assert(EditorState.errorState.now().errorMessage.isDefined)
  }

  test("Clear all selections should not affect node label visibility or error messages") {
    // Set node labels to visible and show error
    EditorState.viewState.update(_.copy(showNodeLabels = true))
    showError("Test error")

    // Create a tiling and set up selections
    selectPolygon(6)
    EditorState.tessellationState.update(_.copy(selectedTilingPolygons = Set(FaceId(1))))
    clearAllSelections()

    // Node labels visibility and error should remain unchanged
    assert(EditorState.viewState.now().showNodeLabels)
    assert(EditorState.errorState.now().errorMessage.isDefined)
  }

  test("Polygon selection preserves existing tiling complexity") {
    // Start with a hexagon tiling
    selectPolygon(6)
    val initialTiling = EditorState.tessellationState.now().currentTiling
    assert(!initialTiling.isEmpty)

    // Simulate growing the tiling by adding selections
    EditorState.tessellationState.update(_.copy(selectedTilingPolygons = Set(FaceId(1), FaceId(2))))
    EditorState.tessellationState.update(_.copy(selectedPerimeterEdges = Set("edge1")))

    // Change polygon selection
    selectPolygon(4)

    // Tiling should remain the same
    assertEquals(EditorState.tessellationState.now().currentTiling, initialTiling)
    // Selections should be preserved
    assertEquals(EditorState.tessellationState.now().selectedTilingPolygons, Set(FaceId(1), FaceId(2)))
    assertEquals(EditorState.tessellationState.now().selectedPerimeterEdges, Set("edge1"))
    // Only the selected polygon type should change
    assertEquals(EditorState.toolState.now().selectedPolygon, Some(4))
  }
