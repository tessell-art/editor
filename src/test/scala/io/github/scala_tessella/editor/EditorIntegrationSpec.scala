package io.github.scala_tessella.editor

import munit.FunSuite
import com.raquo.laminar.api.L.*
import io.github.scala_tessella.editor.models.AppState

class EditorIntegrationSpec extends FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    // Reset state before each test
    AppState.selectedElements.set(Set.empty)
    AppState.selectedPolygon.set(None)
    AppState.viewTransform.set(io.github.scala_tessella.editor.models.ViewTransform())

  test("Polygon selection state should work") {
    // Initially no polygon should be selected
    assert(AppState.selectedPolygon.now().isEmpty)

    // Select a triangle
    AppState.selectedPolygon.set(Some(3))
    assertEquals(AppState.selectedPolygon.now(), Some(3))

    // Deselect
    AppState.selectedPolygon.set(None)
    assert(AppState.selectedPolygon.now().isEmpty)
  }

  test("View transform should update correctly") {
    import io.github.scala_tessella.editor.models.ViewTransform

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