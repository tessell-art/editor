package io.github.scala_tessella.editor

import munit.FunSuite
import io.github.scala_tessella.editor.models.AppState
import io.github.scala_tessella.editor.models.ViewTransform

class EditorLogicSpec extends FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    // Reset state before each test
    AppState.selectedPolygon.set(None)
    AppState.clearTiling() // Start with empty tiling
    AppState.viewTransform.set(ViewTransform())
    AppState.showNodeLabels.set(false) // Start with labels hidden
    AppState.clearError() // Clear any error messages

  test("Editor should start with empty tiling") {
    assert(AppState.isTilingEmpty)
    assert(AppState.currentTiling.now().isEmpty)
  }

  test("Error message should be empty by default") {
    assert(AppState.errorMessage.now().isEmpty)
  }

  test("Show error should set error message") {
    val testMessage = "Test error message"
    AppState.showError(testMessage)
    assertEquals(AppState.errorMessage.now(), Some(testMessage))
  }

  test("Clear error should remove error message") {
    AppState.showError("Test error")
    assert(AppState.errorMessage.now().isDefined)

    AppState.clearError()
    assert(AppState.errorMessage.now().isEmpty)
  }

  test("Node labels should be hidden by default") {
    assert(!AppState.showNodeLabels.now())
  }

  test("Toggle node labels should work") {
    // Initially hidden
    assert(!AppState.showNodeLabels.now())

    // Toggle to show
    AppState.toggleNodeLabels()
    assert(AppState.showNodeLabels.now())

    // Toggle to hide
    AppState.toggleNodeLabels()
    assert(!AppState.showNodeLabels.now())
  }

  test("Polygon selection should create tiling when tiling is empty") {
    // Initially tiling should be empty
    assert(AppState.isTilingEmpty)

    // Select a triangle (3 sides)
    AppState.selectPolygon(3)

    // Now tiling should be created
    assert(!AppState.isTilingEmpty)
    assert(AppState.currentTiling.now().isDefined)
    assertEquals(AppState.selectedPolygon.now(), Some(3))
  }

  test("Polygon selection should not change existing tiling") {
    // Start with a tiling by selecting hexagon
    AppState.selectPolygon(6)
    val initialTiling = AppState.currentTiling.now()
    assert(initialTiling.isDefined)

    // Select a different polygon - should not change tiling
    AppState.selectPolygon(4)
    val afterTiling = AppState.currentTiling.now()

    // Tiling should be the same
    assertEquals(afterTiling, initialTiling)
    // But selection should change
    assertEquals(AppState.selectedPolygon.now(), Some(4))
  }

  test("handlePerimeterEdgeClick should show error when no tiling exists") {
    // Ensure no tiling exists
    assert(AppState.isTilingEmpty)

    // Try to handle edge click - this should show an error
    AppState.handlePerimeterEdgeClick("edge-1", 0)

    // Should show error
    assert(AppState.errorMessage.now().isDefined)
    assert(AppState.errorMessage.now().get.contains("No tiling available"))
  }

  test("handlePerimeterEdgeClick should toggle selection when no polygon selected") {
    // Create a tiling first
    AppState.selectPolygon(6)
    assert(!AppState.isTilingEmpty)

    // Clear polygon selection
    AppState.selectedPolygon.set(None)

    // Handle edge click
    val edgeId = "edge-1"
    AppState.handlePerimeterEdgeClick(edgeId, 0)

    // Should toggle selection (no error because we have a tiling, just no polygon selected)
    assert(AppState.selectedPerimeterEdges.now().contains(edgeId))
  }

  test("Clear tiling should reset to empty state") {
    // Create a tiling
    AppState.selectPolygon(6)
    assert(!AppState.isTilingEmpty)

    // Clear tiling
    AppState.clearTiling()

    // Should be empty again
    assert(AppState.isTilingEmpty)
    assert(AppState.currentTiling.now().isEmpty)
    assert(AppState.selectedTilingPolygons.now().isEmpty)
    assert(AppState.selectedPerimeterEdges.now().isEmpty)
  }

  test("Clear tiling should not affect node label visibility or error messages") {
    // Set node labels to visible and show error
    AppState.showNodeLabels.set(true)
    AppState.showError("Test error")

    // Create and clear tiling
    AppState.selectPolygon(6)
    AppState.clearTiling()

    // Node labels visibility and error should remain unchanged
    assert(AppState.showNodeLabels.now())
    assert(AppState.errorMessage.now().isDefined)
  }

  test("View transform should update correctly") {
    val initialTransform = ViewTransform()
    AppState.viewTransform.set(initialTransform)

    // Test scale update
    AppState.viewTransform.update(t => t.copy(scale = 2.0))
    assertEquals(AppState.viewTransform.now().scale, 2.0)

    // Test rotation update
    AppState.viewTransform.update(t => t.withRotation(45.0))
    assertEquals(AppState.viewTransform.now().rotationDegrees, 45.0)
  }

  test("Tiling polygon selection logic should work") {
    // Create a tiling first
    AppState.selectPolygon(6)
    assert(!AppState.isTilingEmpty)

    val polygonId = "test-polygon"

    // Initially empty
    assert(AppState.selectedTilingPolygons.now().isEmpty)

    // Toggle selection
    AppState.toggleTilingPolygonSelection(polygonId)
    assert(AppState.selectedTilingPolygons.now().contains(polygonId))

    // Toggle again to deselect
    AppState.toggleTilingPolygonSelection(polygonId)
    assert(!AppState.selectedTilingPolygons.now().contains(polygonId))
  }

  test("Perimeter edge selection logic should work") {
    // Create a tiling first
    AppState.selectPolygon(6)
    assert(!AppState.isTilingEmpty)

    val edgeId = "test-edge"

    // Initially empty
    assert(AppState.selectedPerimeterEdges.now().isEmpty)

    // Toggle selection
    AppState.togglePerimeterEdgeSelection(edgeId)
    assert(AppState.selectedPerimeterEdges.now().contains(edgeId))

    // Toggle again to deselect
    AppState.togglePerimeterEdgeSelection(edgeId)
    assert(!AppState.selectedPerimeterEdges.now().contains(edgeId))
  }

  test("Clear all selections should work") {
    // Create a tiling first
    AppState.selectPolygon(6)

    // Set up some selections
    AppState.selectedTilingPolygons.set(Set("poly1"))
    AppState.selectedPerimeterEdges.set(Set("edge1"))

    // Clear all
    AppState.clearAllSelections()

    // Verify all are cleared
    assert(AppState.selectedTilingPolygons.now().isEmpty)
    assert(AppState.selectedPerimeterEdges.now().isEmpty)
  }

  test("Clear all selections should not affect node label visibility or error messages") {
    // Set node labels to visible and show error
    AppState.showNodeLabels.set(true)
    AppState.showError("Test error")

    // Create a tiling and set up selections
    AppState.selectPolygon(6)
    AppState.selectedTilingPolygons.set(Set("poly1"))
    AppState.clearAllSelections()

    // Node labels visibility and error should remain unchanged
    assert(AppState.showNodeLabels.now())
    assert(AppState.errorMessage.now().isDefined)
  }

  test("Multiple error messages should work correctly") {
    // Show first error
    AppState.showError("First error")
    assertEquals(AppState.errorMessage.now(), Some("First error"))

    // Show second error (should replace first)
    AppState.showError("Second error")
    assertEquals(AppState.errorMessage.now(), Some("Second error"))

    // Clear error
    AppState.clearError()
    assert(AppState.errorMessage.now().isEmpty)
  }

  test("Delete selected elements should show appropriate error") {
    // Create a tiling and select some polygons
    AppState.selectPolygon(6)
    AppState.selectedTilingPolygons.set(Set("poly1"))

    // Try to delete - should show error since deletion is not supported
    AppState.deleteSelectedElements()

    // Should show error about deletion not being supported
    assert(AppState.errorMessage.now().isDefined)
    assert(AppState.errorMessage.now().get.contains("deletion not supported"))
  }

  test("Delete selected elements should do nothing when no selections") {
    // Create a tiling but don't select anything
    AppState.selectPolygon(6)
    assert(AppState.selectedTilingPolygons.now().isEmpty)

    // Clear any existing errors
    AppState.clearError()

    // Try to delete - should do nothing
    AppState.deleteSelectedElements()

    // Should not show any error
    assert(AppState.errorMessage.now().isEmpty)
  }

  test("Polygon selection preserves existing tiling complexity") {
    // Start with a hexagon tiling
    AppState.selectPolygon(6)
    val initialTiling = AppState.currentTiling.now()
    assert(initialTiling.isDefined)

    // Simulate growing the tiling by adding selections
    AppState.selectedTilingPolygons.set(Set("poly1", "poly2"))
    AppState.selectedPerimeterEdges.set(Set("edge1"))

    // Change polygon selection
    AppState.selectPolygon(4)

    // Tiling should remain the same
    assertEquals(AppState.currentTiling.now(), initialTiling)
    // Selections should be preserved
    assertEquals(AppState.selectedTilingPolygons.now(), Set("poly1", "poly2"))
    assertEquals(AppState.selectedPerimeterEdges.now(), Set("edge1"))
    // Only the selected polygon type should change
    assertEquals(AppState.selectedPolygon.now(), Some(4))
  }