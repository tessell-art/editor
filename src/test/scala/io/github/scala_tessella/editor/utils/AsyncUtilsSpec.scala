package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.EditorState
import munit.FunSuite

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class AsyncUtilsSpec extends FunSuite with EditorStateFixture:

  test("withLoadingState should set and clear processing state"):
    var executed = false
    assert(!EditorState.isProcessing.now())

    val future = AsyncUtils.withLoadingState { () =>
      // Note: the processing state is set asynchronously after a 50ms delay
      executed = true
      "result"
    }

    // Initially processing should be true
    assert(EditorState.isProcessing.now())

    // Wait for the future to complete
    future.map: result =>
      assert(executed)
      assertEquals(result, "result")
      // Processing state should be cleared after completion
      assert(!EditorState.isProcessing.now())

  test("withLoadingState should clear processing state even on exception"):
    assert(!EditorState.isProcessing.now())

    val future = AsyncUtils.withLoadingState(() =>
      throw new RuntimeException("Test exception")
    )

    // Initially processing should be true
    assert(EditorState.isProcessing.now())

    // Wait for the future to complete
    future.failed.map: exception =>
      assertEquals(exception.getMessage, "Test exception")
      // Processing state should be cleared even after exception
      assert(!EditorState.isProcessing.now())

  test("withLoadingState should return the result of the operation"):
    val future = AsyncUtils.withLoadingState(() => 42)
    future.map: result =>
      assertEquals(result, 42)

  test("withLoadingState should set and clear loading message"):
    assertEquals(EditorState.loadingMessage.now(), None)

    val future = AsyncUtils.withLoadingState(() => "ok", Some("Working..."))

    assertEquals(EditorState.loadingMessage.now(), Some("Working..."))

    future.map: result =>
      assertEquals(result, "ok")
      assertEquals(EditorState.loadingMessage.now(), None)

  test("setLoadingMessage should override message while processing"):
    val future = AsyncUtils.withLoadingState(() => "ok", Some("Starting..."))

    assertEquals(EditorState.loadingMessage.now(), Some("Starting..."))

    AsyncUtils.setLoadingMessage("Halfway there...")
    assertEquals(EditorState.loadingMessage.now(), Some("Halfway there..."))

    future.map: result =>
      assertEquals(result, "ok")
      assertEquals(EditorState.loadingMessage.now(), None)

  test("multiple withLoadingState calls should handle processing state correctly"):
    assert(!EditorState.isProcessing.now())

    val future1 = AsyncUtils.withLoadingState(() => "first")
    assert(EditorState.isProcessing.now())

    val future2 = AsyncUtils.withLoadingState(() => "second")
    assert(EditorState.isProcessing.now())

    // Both futures should complete successfully
    Future.sequence(List(future1, future2)).map: results =>
      assertEquals(results.toSet, Set("first", "second"))
      assert(!EditorState.isProcessing.now())
