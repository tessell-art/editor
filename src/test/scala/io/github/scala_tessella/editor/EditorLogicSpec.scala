
package io.github.scala_tessella.editor

import munit.FunSuite
import io.github.scala_tessella.editor.models.AppState
import io.github.scala_tessella.editor.models.ViewTransform

class EditorLogicSpec extends FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    // Reset state before each test
    AppState.selectedElements.set(Set.empty)
    AppState.selectedPolygon.set(None)
    AppState.clearTiling() // Start with empty tiling
    AppState.viewTransform.set(ViewTransform())

  test("Editor should start with empty tiling") {
    assert(AppState.isTilingEmpty)
    assert(AppState.currentTiling.now().isEmpty)
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

  test("Polygon selection state should work independently") {
    // Initially no polygon should be selected
    assert(AppState.selectedPolygon.now().isEmpty)

    // Select a triangle
    AppState.selectPolygon(3)
    assertEquals(AppState.selectedPolygon.now(), Some(3))

    // Manually deselect (for testing purposes)
    AppState.selectedPolygon.set(None)
    assert(AppState.selectedPolygon.now().isEmpty)
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

  test("Element selection logic should work") {
    val elementId = "test-element"

    // Initially empty
    assert(AppState.selectedElements.now().isEmpty)

    // Toggle selection
    AppState.toggleSelection(elementId)
    assert(AppState.selectedElements.now().contains(elementId))

    // Toggle again to deselect
    AppState.toggleSelection(elementId)
    assert(!AppState.selectedElements.now().contains(elementId))
  }

  test("Clear all selections should work") {
    // Set up some selections
    AppState.selectedElements.set(Set("elem1", "elem2"))
    AppState.selectedTilingPolygons.set(Set("poly1"))
    AppState.selectedPerimeterEdges.set(Set("edge1"))

    // Clear all
    AppState.clearAllSelections()

    // Verify all are cleared
    assert(AppState.selectedElements.now().isEmpty)
    assert(AppState.selectedTilingPolygons.now().isEmpty)
    assert(AppState.selectedPerimeterEdges.now().isEmpty)
  }