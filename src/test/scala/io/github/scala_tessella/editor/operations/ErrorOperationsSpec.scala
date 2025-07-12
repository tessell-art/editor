package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.{EditorState, FailedPolygonPlacement, FailedPolygonDeletion}
import munit.FunSuite

class ErrorOperationsSpec extends FunSuite:

  override def beforeEach(context: BeforeEach): Unit =
    EditorState.errorMessage.set(None)
    EditorState.failedPlacement.set(None)
    EditorState.failedDeletion.set(None)

  test("showError should set error message") {
    val message = "Test error message"
    
    ErrorOperations.showError(message)
    
    assertEquals(EditorState.errorMessage.now(), Some(message))
  }

  test("showError should replace existing error message") {
    EditorState.errorMessage.set(Some("Old error"))
    
    ErrorOperations.showError("New error")
    
    assertEquals(EditorState.errorMessage.now(), Some("New error"))
  }

  test("clearError should remove error message") {
    EditorState.errorMessage.set(Some("Test error"))
    
    ErrorOperations.clearError()
    
    assertEquals(EditorState.errorMessage.now(), None)
  }
