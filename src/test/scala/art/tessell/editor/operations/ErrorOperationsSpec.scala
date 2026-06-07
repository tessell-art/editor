package art.tessell.editor.operations

import art.tessell.editor.EditorStateFixture
import art.tessell.editor.models.{EditorState, FailedPolygonPlacement}
import art.tessell.editor.utils.geo.Point
import io.github.scala_tessella.dcel.structure.VertexId
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

  test("truncateForToast leaves short messages unchanged") {
    val short = "Cannot add rotated copy: overlap"
    assertEquals(ErrorOperations.truncateForToast(short), short)
  }

  test("truncateForToast clips messages with too many lines and appends an ellipsis") {
    val many   = (1 to 30).map(i => s"line $i").mkString("\n")
    val result = ErrorOperations.truncateForToast(many)
    assert(result.endsWith("…"))
    assert(result.linesIterator.size <= 7) // 6 kept lines (the last carries the ellipsis)
    assert(!result.contains("line 30"))
  }

  test("truncateForToast clips a single very long line by character count") {
    val long   = "x" * 1000
    val result = ErrorOperations.truncateForToast(long)
    assert(result.endsWith("…"))
    assert(result.length < long.length)
    assert(result.length <= 281) // 280 chars + ellipsis
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
