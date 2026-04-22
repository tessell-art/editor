package io.github.scala_tessella.editor.models

import io.github.scala_tessella.dcel.structure.{FaceId, VertexId}
import io.github.scala_tessella.editor.{AppState, EditorStateFixture}
import io.github.scala_tessella.editor.utils.ColorRGB
import io.github.scala_tessella.editor.utils.geo.{Point, Radian}
import munit.FunSuite

class AppStateSpec extends FunSuite with EditorStateFixture:

  test("toggleEditorMode should switch from Select to Delete") {
    assertEquals(EditorState.editorMode.now(), EditorMode.Select)
    AppState.toggleEditorMode()
    assertEquals(EditorState.editorMode.now(), EditorMode.Delete)
  }

  test("toggleEditorMode should switch from Delete to Select") {
    EditorState.editorMode.set(EditorMode.Delete)
    assertEquals(EditorState.editorMode.now(), EditorMode.Delete)
    AppState.toggleEditorMode()
    assertEquals(EditorState.editorMode.now(), EditorMode.Select)
  }

  test("clearMeasurements should reset all measurement-related state") {
    // Given: some measurement state is set
    val point = ClickablePoint(Point(1, 1), Anchor.Vertex(VertexId(1)))
    EditorState.clickablePoints.set(List(point))
    EditorState.measurementStartPoint.set(Some(point))
    EditorState.measurementEndPoint.set(Some(point))
    EditorState.measurementResult.set(Some(123.45))
    EditorState.measurementAngle.set(Some(Radian(0.5)))
    EditorState.highlightedPolygonId.set(Some(FaceId(1)))
    EditorState.measurementPreviousEndPoint.set(Some(Point(0, 0)))

    // When
    AppState.clearMeasurements()

    // Then
    assertEquals(EditorState.clickablePoints.now(), Nil)
    assertEquals(EditorState.measurementStartPoint.now(), None)
    assertEquals(EditorState.measurementEndPoint.now(), None)
    assertEquals(EditorState.measurementPreviousEndPoint.now(), None)
    assertEquals(EditorState.highlightedPolygonId.now(), None)
    assertEquals(EditorState.measurementResult.now(), None)
    assertEquals(EditorState.measurementAngle.now(), None)
  }

  test("refreshSettingsTempValues syncs temp settings and hides picker") {
    EditorState.defaultStartFillColor.set(ColorRGB(10, 20, 30))
    EditorState.perimeterEdgeColor.set(ColorRGB(40, 50, 60))
    EditorState.tempDefaultFillColor.set(ColorRGB(1, 2, 3))
    EditorState.tempPerimeterEdgeColor.set(ColorRGB(4, 5, 6))
    EditorState.tempSettingsPickerColor.set(ColorRGB(7, 8, 9))
    EditorState.showSettingsColorPicker.set(true)

    AppState.refreshSettingsTempValues()

    assertEquals(EditorState.tempDefaultFillColor.now(), EditorState.defaultStartFillColor.now())
    assertEquals(EditorState.tempPerimeterEdgeColor.now(), EditorState.perimeterEdgeColor.now())
    assertEquals(EditorState.tempSettingsPickerColor.now(), EditorState.defaultStartFillColor.now())
    assertEquals(EditorState.showSettingsColorPicker.now(), false)
  }
