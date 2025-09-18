package io.github.scala_tessella.editor.models

import io.github.scala_tessella.dcel.{FaceId, VertexId}
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.utils.Geometry.Point
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
    val point = ClickablePoint(Point(1, 1), Anchor.Vertex(VertexId("V1")))
    EditorState.clickablePoints.set(List(point))
    EditorState.measurementStartPoint.set(Some(point))
    EditorState.measurementEndPoint.set(Some(point))
    EditorState.measurementResult.set(Some(123.45))
    EditorState.measurementAngle.set(Some(0.5))
    EditorState.highlightedPolygonId.set(Some(FaceId("F1")))
    EditorState.measurementPreviousEndPoint.set(Some(Point(0,0)))

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
