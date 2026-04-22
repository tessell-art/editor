package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.structure.{FaceId, VertexId}
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.{Anchor, ClickablePoint, EditorState, MeasurementState}
import io.github.scala_tessella.editor.utils.geo.{Point, Radian}
import munit.FunSuite

class MeasurementOperationsSpec extends FunSuite with EditorStateFixture:

  test("clearAll resets all per-measurement fields") {
    val point = ClickablePoint(Point(1, 1), Anchor.Vertex(VertexId(1)))
    EditorState.measurementState.update(_.copy(
      clickablePoints = List(point),
      measurementStartPoint = Some(point),
      measurementEndPoint = Some(point),
      measurementPreviousEndPoint = Some(Point(0, 0)),
      highlightedPolygonId = Some(FaceId(1)),
      measurementResult = Some(123.45),
      measurementAngle = Some(Radian(0.5))
    ))

    MeasurementOperations.clearAll()

    val state = EditorState.measurementState.now()
    assertEquals(state.clickablePoints, Nil)
    assertEquals(state.measurementStartPoint, None)
    assertEquals(state.measurementEndPoint, None)
    assertEquals(state.measurementPreviousEndPoint, None)
    assertEquals(state.highlightedPolygonId, None)
    assertEquals(state.measurementResult, None)
    assertEquals(state.measurementAngle, None)
  }

  test("clearAll preserves isAngleShownInRad (a user preference, not per-measurement state)") {
    EditorState.measurementState.update(_.copy(
      isAngleShownInRad = false,
      measurementResult = Some(42.0)
    ))

    MeasurementOperations.clearAll()

    assertEquals(EditorState.measurementState.now().isAngleShownInRad, false)
    assertEquals(EditorState.measurementState.now().measurementResult, None)
  }

  test("clearAll on already-initial state leaves it unchanged") {
    EditorState.measurementState.set(MeasurementState.initial)

    MeasurementOperations.clearAll()

    assertEquals(EditorState.measurementState.now(), MeasurementState.initial)
  }
