package io.github.scala_tessella.editor.models

import io.github.scala_tessella.tessella.Geometry.Point
import io.github.scala_tessella.tessella.IncrementalTiling.Strictness
import io.github.scala_tessella.tessella.Topology.Node
import munit.FunSuite

class AppStateSpec extends FunSuite {

  override def beforeEach(context: BeforeEach): Unit = {
    // Reset any relevant state before each test
    EditorState.editorMode.set(EditorMode.Select)
    EditorState.strictness.set(Strictness.STRICT)
    EditorState.clickablePoints.set(Nil)
    EditorState.measurementStartPoint.set(None)
    EditorState.measurementEndPoint.set(None)
    EditorState.measurementResult.set(None)
    EditorState.measurementAngle.set(None)
    EditorState.highlightedPolygonId.set(None)
    EditorState.measurementPreviousEndPoint.set(None)
  }

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

  test("toggleStrictness should switch from STRICT to CROSSING") {
    assertEquals(EditorState.strictness.now(), Strictness.STRICT)
    AppState.toggleStrictness()
    assertEquals(EditorState.strictness.now(), Strictness.CROSSING)
  }

  test("toggleStrictness should switch from CROSSING to STRICT") {
    EditorState.strictness.set(Strictness.CROSSING)
    assertEquals(EditorState.strictness.now(), Strictness.CROSSING)
    AppState.toggleStrictness()
    assertEquals(EditorState.strictness.now(), Strictness.STRICT)
  }

  test("clearMeasurements should reset all measurement-related state") {
    // Given: some measurement state is set
    val point = ClickablePoint(Point(1, 1), Anchor.Vertex(Node(1)))
    EditorState.clickablePoints.set(List(point))
    EditorState.measurementStartPoint.set(Some(point))
    EditorState.measurementEndPoint.set(Some(point))
    EditorState.measurementResult.set(Some(123.45))
    EditorState.measurementAngle.set(Some(0.5))
    EditorState.highlightedPolygonId.set(Some("poly1"))
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

}
