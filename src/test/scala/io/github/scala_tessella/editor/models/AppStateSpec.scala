package io.github.scala_tessella.editor.models

import io.github.scala_tessella.dcel.structure.{FaceId, VertexId}
import io.github.scala_tessella.editor.{AppState, EditorStateFixture}
import io.github.scala_tessella.editor.utils.ColorRGB
import io.github.scala_tessella.editor.utils.geo.{Point, Radian}
import munit.FunSuite

class AppStateSpec extends FunSuite with EditorStateFixture:

  test("toggleEditorMode should switch from Select to Delete") {
    assertEquals(EditorState.toolState.now().editorMode, EditorMode.Select)
    AppState.toggleEditorMode()
    assertEquals(EditorState.toolState.now().editorMode, EditorMode.Delete)
  }

  test("toggleEditorMode should switch from Delete to Select") {
    EditorState.toolState.update(_.copy(editorMode = EditorMode.Delete))
    assertEquals(EditorState.toolState.now().editorMode, EditorMode.Delete)
    AppState.toggleEditorMode()
    assertEquals(EditorState.toolState.now().editorMode, EditorMode.Select)
  }

  test("clearMeasurements should reset all measurement-related state") {
    // Given: some measurement state is set
    val point = ClickablePoint(Point(1, 1), Anchor.Vertex(VertexId(1)))
    EditorState.measurementState.update(_.copy(
      clickablePoints = List(point),
      measurementStartPoint = Some(point),
      measurementEndPoint = Some(point),
      measurementResult = Some(123.45),
      measurementAngle = Some(Radian(0.5)),
      highlightedPolygonId = Some(FaceId(1)),
      measurementPreviousEndPoint = Some(Point(0, 0))
    ))

    // When
    AppState.clearMeasurements()

    // Then
    assertEquals(EditorState.measurementState.now().clickablePoints, Nil)
    assertEquals(EditorState.measurementState.now().measurementStartPoint, None)
    assertEquals(EditorState.measurementState.now().measurementEndPoint, None)
    assertEquals(EditorState.measurementState.now().measurementPreviousEndPoint, None)
    assertEquals(EditorState.measurementState.now().highlightedPolygonId, None)
    assertEquals(EditorState.measurementState.now().measurementResult, None)
    assertEquals(EditorState.measurementState.now().measurementAngle, None)
  }

  test("refreshSettingsTempValues syncs temp settings and hides picker") {
    EditorState.colorState.update(_.copy(
      defaultStartFillColor = ColorRGB(10, 20, 30),
      perimeterEdgeColor = ColorRGB(40, 50, 60),
      tempDefaultFillColor = ColorRGB(1, 2, 3),
      tempPerimeterEdgeColor = ColorRGB(4, 5, 6),
      tempSettingsPickerColor = ColorRGB(7, 8, 9)
    ))
    EditorState.popupState.update(_.copy(showSettingsColorPicker = true))

    AppState.refreshSettingsTempValues()

    assertEquals(
      EditorState.colorState.now().tempDefaultFillColor,
      EditorState.colorState.now().defaultStartFillColor
    )
    assertEquals(
      EditorState.colorState.now().tempPerimeterEdgeColor,
      EditorState.colorState.now().perimeterEdgeColor
    )
    assertEquals(
      EditorState.colorState.now().tempSettingsPickerColor,
      EditorState.colorState.now().defaultStartFillColor
    )
    assertEquals(EditorState.popupState.now().showSettingsColorPicker, false)
  }
