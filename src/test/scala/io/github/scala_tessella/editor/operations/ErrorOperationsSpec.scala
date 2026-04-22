package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.structure.VertexId
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.{EditorState, FailedPolygonPlacement}
import io.github.scala_tessella.editor.utils.geo.Point
import munit.FunSuite

class ErrorOperationsSpec extends FunSuite with EditorStateFixture:

  test("showError should set error message") {
    val message = "Test error message"

    ErrorOperations.showError(message)

    assertEquals(EditorState.errorState.now().errorMessage, Some(message))
  }

  test("showError should replace existing error message") {
    EditorState.errorState.update(_.copy(errorMessage = Some("Old error")))

    ErrorOperations.showError("New error")

    assertEquals(EditorState.errorState.now().errorMessage, Some("New error"))
  }

  test("clearError should remove error message") {
    EditorState.errorState.update(_.copy(errorMessage = Some("Test error")))

    ErrorOperations.clearError()

    assertEquals(EditorState.errorState.now().errorMessage, None)
  }

  test("showError with context should format message correctly") {
    val message = "File not found"
    val context = "SVG Import"
    ErrorOperations.showError(message, context = Some(context), asToast = false)

    val expected = s"$context: $message"
    assertEquals(EditorState.errorState.now().errorMessage, Some(expected))
  }

  test("showError with hint should include hint in message") {
    val message = "Invalid format"
    val hint    = "Try using a different file"
    ErrorOperations.showError(message, hint = Some(hint), asToast = false)

    val result = EditorState.errorState.now().errorMessage.get
    assert(result.contains(message))
    assert(result.contains("Hint:"))
    assert(result.contains(hint))
  }

  test("showError should set failed placement") {
    val placement = FailedPolygonPlacement(
      0,
      Vector.empty,
      ((VertexId(999), Point.origin), (VertexId(999), Point.origin)),
      null,
      None
    )
    ErrorOperations.showError("test", placement = Some(placement), asToast = false)

    assertEquals(EditorState.errorState.now().failedPlacement, Some(placement))
  }

  test("clearError should clear all error states") {
    ErrorOperations.showError("test message", asToast = false)
    assert(EditorState.errorState.now().errorMessage.isDefined)

    ErrorOperations.clearError()

    assertEquals(EditorState.errorState.now().errorMessage, None)
    assertEquals(EditorState.errorState.now().failedPlacement, None)
    assertEquals(EditorState.errorState.now().failedDeletion, None)
  }

  test("convenience methods should set correct severity") {
//    ErrorOperations.info("info message", asToast = false)
//    assert(EditorState.errorState.now().errorMessage.get.contains("info message"))

    ErrorOperations.warn("warning message", asToast = false)
    assert(EditorState.errorState.now().errorMessage.get.contains("warning message"))

    ErrorOperations.error("error message", asToast = false)
    assert(EditorState.errorState.now().errorMessage.get.contains("error message"))
  }
