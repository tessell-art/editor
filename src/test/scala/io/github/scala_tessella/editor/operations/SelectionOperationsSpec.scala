package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.structure.FaceId
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.{Anchor, EditorMode, EditorState, Tool}
import io.github.scala_tessella.editor.utils.{ColorRGB, TilingBuilders}
import munit.FunSuite

class SelectionOperationsSpec extends FunSuite with EditorStateFixture:

  test("ColorPicker tool should update fill color and deactivate") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id
    val color  = ColorRGB(10, 20, 30)

    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.colorState.update(_.copy(polygonColors = Map(faceId -> color)))
    EditorState.toolState.update(_.copy(activeTool = Some(Tool.ColorPicker)))
    EditorState.colorState.update(_.copy(fillColor = ColorRGB(1, 1, 1)))

    SelectionOperations.handleTilingPolygonClick(faceId)

    assertEquals(EditorState.colorState.now().fillColor, color)
    assertEquals(EditorState.toolState.now().activeTool, None)
  }

  test("ShapeAndColorPicker should select polygon shape and color") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id
    val color  = ColorRGB(5, 6, 7)

    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.colorState.update(_.copy(polygonColors = Map(faceId -> color)))
    EditorState.toolState.update(_.copy(activeTool = Some(Tool.ShapeAndColorPicker)))
    EditorState.toolState.update(_.copy(selectedPolygon = None))
    EditorState.isIrregularSelected.set(true)

    SelectionOperations.handleTilingPolygonClick(faceId)

    assertEquals(EditorState.colorState.now().fillColor, color)
    assertEquals(EditorState.toolState.now().selectedPolygon, Some(4))
    assertEquals(EditorState.isIrregularSelected.now(), false)
    assertEquals(EditorState.toolState.now().activeTool, None)
  }

  test("SelectByColor should select all polygons with same color") {
    val f1 = FaceId(1)
    val f2 = FaceId(2)
    val f3 = FaceId(3)
    val c1 = ColorRGB(100, 100, 100)
    val c2 = ColorRGB(200, 200, 200)

    EditorState.colorState.update(_.copy(polygonColors = Map(f1 -> c1, f2 -> c1, f3 -> c2)))
    EditorState.toolState.update(_.copy(activeTool = Some(Tool.SelectByColor)))

    SelectionOperations.handleTilingPolygonClick(f1)

    assertEquals(EditorState.tessellationState.now().selectedTilingPolygons, Set(f1, f2))
    assertEquals(EditorState.toolState.now().activeTool, None)
  }

  test("Measurement tool should populate clickable points and highlight face") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id

    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.toolState.update(_.copy(activeTool = Some(Tool.Measurement)))

    SelectionOperations.handleTilingPolygonClick(faceId)

    assertEquals(EditorState.measurementState.now().highlightedPolygonId, Some(faceId))
    assert(EditorState.measurementState.now().clickablePoints.nonEmpty)
  }

  test("Fan tool should only expose vertex clickable points") {
    val tiling      = TilingBuilders.freshSquare()
    val faceId      = tiling.innerFaces.head.id
    val vertexCount = tiling.findInnerFaceVertices(faceId).toOption.get.size

    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.toolState.update(_.copy(activeTool = Some(Tool.Fan)))

    SelectionOperations.handleTilingPolygonClick(faceId)

    val points = EditorState.measurementState.now().clickablePoints
    assertEquals(EditorState.measurementState.now().highlightedPolygonId, Some(faceId))
    assertEquals(points.size, vertexCount)
    assert(points.forall(p =>
      p.anchor match {
        case Anchor.Vertex(_) => true
        case _                => false
      }
    ))
  }

  test("Inserter tool should highlight face and not create clickable points") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id

    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.toolState.update(_.copy(activeTool = Some(Tool.Inserter)))

    SelectionOperations.handleTilingPolygonClick(faceId)

    assertEquals(EditorState.measurementState.now().highlightedPolygonId, Some(faceId))
    assertEquals(EditorState.measurementState.now().clickablePoints, Nil)
  }

  test("Select mode should toggle polygon selection") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id

    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.toolState.update(_.copy(activeTool = None, editorMode = EditorMode.Select))

    SelectionOperations.handleTilingPolygonClick(faceId)
    assertEquals(EditorState.tessellationState.now().selectedTilingPolygons, Set(faceId))

    SelectionOperations.handleTilingPolygonClick(faceId)
    assertEquals(EditorState.tessellationState.now().selectedTilingPolygons, Set.empty)
  }

  test("SelectByColor should deactivate even when no color is assigned") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id
    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.colorState.update(_.copy(polygonColors = Map.empty))
    EditorState.toolState.update(_.copy(activeTool = Some(Tool.SelectByColor)))

    SelectionOperations.handleTilingPolygonClick(faceId)

    assertEquals(EditorState.tessellationState.now().selectedTilingPolygons, Set.empty)
    assertEquals(EditorState.toolState.now().activeTool, None)
  }

  test("ShapeAndColorPicker should deactivate even when no color is assigned") {
    val tiling = TilingBuilders.freshSquare()
    val faceId = tiling.innerFaces.head.id
    EditorState.tessellationState.update(_.copy(currentTiling = tiling))
    EditorState.colorState.update(_.copy(polygonColors = Map.empty))
    EditorState.toolState.update(_.copy(activeTool = Some(Tool.ShapeAndColorPicker)))

    SelectionOperations.handleTilingPolygonClick(faceId)

    assertEquals(EditorState.toolState.now().activeTool, Some(Tool.ShapeAndColorPicker))
    assertEquals(EditorState.toolState.now().selectedPolygon, None)
    assertEquals(EditorState.isIrregularSelected.now(), false)
  }

  test("handlePerimeterEdgeClick should show error when tiling is empty and a polygon is selected") {
    EditorState.tessellationState.update(_.copy(currentTiling =
      io.github.scala_tessella.dcel.TilingDCEL.empty
    ))
    EditorState.toolState.update(_.copy(selectedPolygon = Some(4)))
    EditorState.isIrregularSelected.set(false)
    EditorState.errorMessage.set(None)

    SelectionOperations.handlePerimeterEdgeClick("edge-1", 0)

    assert(EditorState.errorMessage.now().exists(_.contains("No tiling available to grow")))
  }
