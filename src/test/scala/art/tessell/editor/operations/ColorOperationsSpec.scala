package art.tessell.editor.operations

import art.tessell.editor.EditorStateFixture
import art.tessell.editor.models.{EditorConfig, EditorState}
import art.tessell.editor.utils.ColorRGB
import io.github.scala_tessella.dcel.structure.FaceId
import munit.FunSuite

class ColorOperationsSpec extends FunSuite with EditorStateFixture:

  override def beforeEach(context: BeforeEach): Unit = {
    super.beforeEach(context)
    // Suite-specific test defaults
    EditorState.colorState.update(_.copy(fillColor = ColorRGB(255, 0, 0)))
  }

  val F1: FaceId = FaceId(1)
  val F2: FaceId = FaceId(2)
  val F3: FaceId = FaceId(3)
  val F4: FaceId = FaceId(4)

  test("applyColorToSelectedPolygons should apply the current fill color to selected polygons") {
    // Given
    val selectedIds = Set(F1, F2)
    EditorState.tessellationState.update(_.copy(selectedTilingPolygons = selectedIds))
    val color       = ColorRGB(100, 150, 200)
    EditorState.colorState.update(_.copy(fillColor = color))

    // When
    ColorOperations.applyColorToSelectedPolygons(color)

    // Then
    val colors = EditorState.colorState.now().polygonColors
    assertEquals(colors.get(F1), Some(color))
    assertEquals(colors.get(F2), Some(color))
  }

  test("applyColorToSelectedPolygons should not change colors of unselected polygons") {
    // Given
    EditorState.colorState.update(_.copy(polygonColors = Map(F3 -> ColorRGB(0, 0, 255))))
    val selectedIds = Set(F1)
    EditorState.tessellationState.update(_.copy(selectedTilingPolygons = selectedIds))
    val color       = ColorRGB(100, 150, 200)

    // When
    ColorOperations.applyColorToSelectedPolygons(color)

    // Then
    val colors = EditorState.colorState.now().polygonColors
    assertEquals(colors.get(F1), Some(color))
    assertEquals(colors.get(F3), Some(ColorRGB(0, 0, 255))) // Should be unchanged
  }

  test("applyColorToSelectedPolygons should do nothing if no polygons are selected") {
    // Given
    val initialColors = Map(F3 -> ColorRGB(0, 0, 255))
    EditorState.colorState.update(_.copy(polygonColors = initialColors))
    EditorState.tessellationState.update(_.copy(selectedTilingPolygons = Set.empty))
    val color         = ColorRGB(100, 150, 200)

    // When
    ColorOperations.applyColorToSelectedPolygons(color)

    // Then
    assertEquals(EditorState.colorState.now().polygonColors, initialColors)
  }

  test("getPolygonColor should return None when color is missing") {
    EditorState.colorState.update(_.copy(polygonColors = Map.empty))
    assertEquals(ColorOperations.getPolygonColor(F1), None)
  }

  test("setPolygonColor should update the color map") {
    val color = ColorRGB(10, 20, 30)
    ColorOperations.setPolygonColor(F1, color)
    assertEquals(EditorState.colorState.now().polygonColors.get(F1), Some(color))
  }

  test("ensureColorsForFaces should assign defaults only when missing") {
    val existing = ColorRGB(1, 2, 3)
    EditorState.colorState.update(_.copy(polygonColors = Map(F1 -> existing)))

    ColorOperations.ensureColorsForFaces(List(F1, F2), EditorConfig.defaultPolygonColor)

    val colors = EditorState.colorState.now().polygonColors
    assertEquals(colors.get(F1), Some(existing))
    assertEquals(colors.get(F2), Some(EditorConfig.defaultPolygonColor))
  }

  test("syncColorsForFaces should remove stale colors and add missing defaults") {
    val existing1 = ColorRGB(1, 2, 3)
    val existing2 = ColorRGB(4, 5, 6)
    val stale     = ColorRGB(7, 8, 9)
    val default   = ColorRGB(11, 12, 13)

    EditorState.colorState.update(_.copy(polygonColors = Map(F1 -> existing1, F2 -> existing2, F3 -> stale)))

    ColorOperations.syncColorsForFaces(List(F1, F2, F4), default)

    val colors = EditorState.colorState.now().polygonColors
    assertEquals(colors.get(F1), Some(existing1))
    assertEquals(colors.get(F2), Some(existing2))
    assertEquals(colors.get(F4), Some(default))
    assert(!colors.contains(F3))
  }
