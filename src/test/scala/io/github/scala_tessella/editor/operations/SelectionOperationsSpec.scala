package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.structure.FaceId
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.{EditorMode, EditorState, Tool}
import io.github.scala_tessella.editor.utils.{ColorRGB, TilingBuilders}
import munit.FunSuite

class SelectionOperationsSpec extends FunSuite with EditorStateFixture:

  test("ColorPicker tool should update fill color and deactivate") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id
    val color  = ColorRGB(10, 20, 30)

    EditorState.currentTiling.set(tiling)
    EditorState.polygonColors.set(Map(faceId -> color))
    EditorState.activeTool.set(Some(Tool.ColorPicker))
    EditorState.fillColor.set(ColorRGB(1, 1, 1))

    SelectionOperations.handleTilingPolygonClick(faceId)

    assertEquals(EditorState.fillColor.now(), color)
    assertEquals(EditorState.activeTool.now(), None)
  }

  test("ShapeAndColorPicker should select polygon shape and color") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id
    val color  = ColorRGB(5, 6, 7)

    EditorState.currentTiling.set(tiling)
    EditorState.polygonColors.set(Map(faceId -> color))
    EditorState.activeTool.set(Some(Tool.ShapeAndColorPicker))
    EditorState.selectedPolygon.set(None)
    EditorState.isIrregularSelected.set(true)

    SelectionOperations.handleTilingPolygonClick(faceId)

    assertEquals(EditorState.fillColor.now(), color)
    assertEquals(EditorState.selectedPolygon.now(), Some(4))
    assertEquals(EditorState.isIrregularSelected.now(), false)
    assertEquals(EditorState.activeTool.now(), None)
  }

  test("SelectByColor should select all polygons with same color") {
    val f1 = FaceId(1)
    val f2 = FaceId(2)
    val f3 = FaceId(3)
    val c1 = ColorRGB(100, 100, 100)
    val c2 = ColorRGB(200, 200, 200)

    EditorState.polygonColors.set(Map(f1 -> c1, f2 -> c1, f3 -> c2))
    EditorState.activeTool.set(Some(Tool.SelectByColor))

    SelectionOperations.handleTilingPolygonClick(f1)

    assertEquals(EditorState.selectedTilingPolygons.now(), Set(f1, f2))
    assertEquals(EditorState.activeTool.now(), None)
  }

  test("Measurement tool should populate clickable points and highlight face") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id

    EditorState.currentTiling.set(tiling)
    EditorState.activeTool.set(Some(Tool.Measurement))

    SelectionOperations.handleTilingPolygonClick(faceId)

    assertEquals(EditorState.highlightedPolygonId.now(), Some(faceId))
    assert(EditorState.clickablePoints.now().nonEmpty)
  }

  test("Inserter tool should highlight face and not create clickable points") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id

    EditorState.currentTiling.set(tiling)
    EditorState.activeTool.set(Some(Tool.Inserter))

    SelectionOperations.handleTilingPolygonClick(faceId)

    assertEquals(EditorState.highlightedPolygonId.now(), Some(faceId))
    assertEquals(EditorState.clickablePoints.now(), Nil)
  }

  test("Select mode should toggle polygon selection") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id

    EditorState.currentTiling.set(tiling)
    EditorState.activeTool.set(None)
    EditorState.editorMode.set(EditorMode.Select)

    SelectionOperations.handleTilingPolygonClick(faceId)
    assertEquals(EditorState.selectedTilingPolygons.now(), Set(faceId))

    SelectionOperations.handleTilingPolygonClick(faceId)
    assertEquals(EditorState.selectedTilingPolygons.now(), Set.empty)
  }
