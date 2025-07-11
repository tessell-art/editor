package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.EditorState
import munit.FunSuite

class ColorOperationsSpec extends FunSuite:

  override def beforeEach(context: BeforeEach): Unit = {
    // Reset state before each test
    EditorState.polygonColors.set(Map.empty)
    EditorState.selectedTilingPolygons.set(Set.empty)
    EditorState.fillColor.set((255, 0, 0)) // Default test color
  }

  test("applyColorToSelectedPolygons should apply the current fill color to selected polygons") {
    // Given
    val selectedIds = Set("poly1", "poly2")
    EditorState.selectedTilingPolygons.set(selectedIds)
    val color = (100, 150, 200)
    EditorState.fillColor.set(color)

    // When
    ColorOperations.applyColorToSelectedPolygons(color)

    // Then
    val colors = EditorState.polygonColors.now()
    assertEquals(colors.get("poly1"), Some(color))
    assertEquals(colors.get("poly2"), Some(color))
  }

  test("applyColorToSelectedPolygons should not change colors of unselected polygons") {
    // Given
    EditorState.polygonColors.set(Map("poly3" -> (0, 0, 255)))
    val selectedIds = Set("poly1")
    EditorState.selectedTilingPolygons.set(selectedIds)
    val color = (100, 150, 200)

    // When
    ColorOperations.applyColorToSelectedPolygons(color)

    // Then
    val colors = EditorState.polygonColors.now()
    assertEquals(colors.get("poly1"), Some(color))
    assertEquals(colors.get("poly3"), Some((0, 0, 255))) // Should be unchanged
  }

  test("applyColorToSelectedPolygons should do nothing if no polygons are selected") {
    // Given
    val initialColors = Map("poly3" -> (0, 0, 255))
    EditorState.polygonColors.set(initialColors)
    EditorState.selectedTilingPolygons.set(Set.empty)
    val color = (100, 150, 200)

    // When
    ColorOperations.applyColorToSelectedPolygons(color)

    // Then
    assertEquals(EditorState.polygonColors.now(), initialColors)
  }

  test("getOrAssignPolygonColor should return existing color if available") {
    // Given
    val existingColor = (123, 45, 67)
    EditorState.polygonColors.set(Map("poly1" -> existingColor))

    // When
    val result = ColorOperations.getOrAssignPolygonColor("poly1")

    // Then
    assertEquals(result, existingColor)
  }

  test("getOrAssignPolygonColor should assign a new color if not available") {
    // Given: No color for "poly1"
    
    // When
    val result = ColorOperations.getOrAssignPolygonColor("poly1")

    // Then
    val assignedColor = EditorState.polygonColors.now().get("poly1")
    assert(assignedColor.isDefined, "A color should have been assigned")
    assertEquals(result, assignedColor.get)
  }

  test("getOrAssignPolygonColor should not reassign color on multiple calls") {
    // When
    val firstResult = ColorOperations.getOrAssignPolygonColor("poly1")
    val secondResult = ColorOperations.getOrAssignPolygonColor("poly1")

    // Then
    assertEquals(firstResult, secondResult)
    assertEquals(EditorState.polygonColors.now().size, 1)
  }
