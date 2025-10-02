package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.structure.FaceId
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.ColorRGB
import munit.FunSuite

class ColorOperationsSpec extends FunSuite with EditorStateFixture:

  override def beforeEach(context: BeforeEach): Unit = {
    super.beforeEach(context)
    // Suite-specific test defaults
    EditorState.fillColor.set(ColorRGB(255, 0, 0))
  }

  val F1: FaceId = FaceId("F1")
  val F2: FaceId = FaceId("F2")
  val F3: FaceId = FaceId("F3")

  test("applyColorToSelectedPolygons should apply the current fill color to selected polygons") {
    // Given
    val selectedIds = Set(F1, F2)
    EditorState.selectedTilingPolygons.set(selectedIds)
    val color       = ColorRGB(100, 150, 200)
    EditorState.fillColor.set(color)

    // When
    ColorOperations.applyColorToSelectedPolygons(color)

    // Then
    val colors = EditorState.polygonColors.now()
    assertEquals(colors.get(F1), Some(color))
    assertEquals(colors.get(F2), Some(color))
  }

  test("applyColorToSelectedPolygons should not change colors of unselected polygons") {
    // Given
    EditorState.polygonColors.set(Map(F3 -> ColorRGB(0, 0, 255)))
    val selectedIds = Set(F1)
    EditorState.selectedTilingPolygons.set(selectedIds)
    val color       = ColorRGB(100, 150, 200)

    // When
    ColorOperations.applyColorToSelectedPolygons(color)

    // Then
    val colors = EditorState.polygonColors.now()
    assertEquals(colors.get(F1), Some(color))
    assertEquals(colors.get(F3), Some(ColorRGB(0, 0, 255))) // Should be unchanged
  }

  test("applyColorToSelectedPolygons should do nothing if no polygons are selected") {
    // Given
    val initialColors = Map(F3 -> ColorRGB(0, 0, 255))
    EditorState.polygonColors.set(initialColors)
    EditorState.selectedTilingPolygons.set(Set.empty)
    val color         = ColorRGB(100, 150, 200)

    // When
    ColorOperations.applyColorToSelectedPolygons(color)

    // Then
    assertEquals(EditorState.polygonColors.now(), initialColors)
  }

  test("getOrAssignPolygonColor should return existing color if available") {
    // Given
    val existingColor = ColorRGB(123, 45, 67)
    EditorState.polygonColors.set(Map(F1 -> existingColor))

    // When
    val result = ColorOperations.getOrAssignPolygonColor(F1)

    // Then
    assertEquals(result, existingColor)
  }

  test("getOrAssignPolygonColor should assign a new color if not available") {
    // Given: No color for "poly1"

    // When
    val result = ColorOperations.getOrAssignPolygonColor(F1)

    // Then
    val assignedColor = EditorState.polygonColors.now().get(F1)
    assert(assignedColor.isDefined, "A color should have been assigned")
    assertEquals(result, assignedColor.get)
  }

  test("getOrAssignPolygonColor should not reassign color on multiple calls") {
    // When
    val firstResult  = ColorOperations.getOrAssignPolygonColor(F1)
    val secondResult = ColorOperations.getOrAssignPolygonColor(F1)

    // Then
    assertEquals(firstResult, secondResult)
    assertEquals(EditorState.polygonColors.now().size, 1)
  }
