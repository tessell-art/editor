package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.EditorState
import munit.FunSuite

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

class AsyncUtilsSpec extends FunSuite with EditorStateFixture:

  test("withLoadingState should set and clear processing state") {
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
    future.onComplete {
      case Success(result)    =>
        assert(executed)
        assertEquals(result, "result")
        // Processing state should be cleared after completion
        assert(!EditorState.isProcessing.now())
      case Failure(exception) =>
        fail(s"Unexpected failure: $exception")
    }
  }

  test("withLoadingState should clear processing state even on exception") {
    assert(!EditorState.isProcessing.now())

    val future = AsyncUtils.withLoadingState(() =>
      throw new RuntimeException("Test exception")
    )

    // Initially processing should be true
    assert(EditorState.isProcessing.now())

    // Wait for the future to complete
    future.onComplete {
      case Success(_)         =>
        fail("Expected failure but got success")
      case Failure(exception) =>
        assertEquals(exception.getMessage, "Test exception")
        // Processing state should be cleared even after exception
        assert(!EditorState.isProcessing.now())
    }
  }

  test("withLoadingState should return the result of the operation") {
    val future = AsyncUtils.withLoadingState(() =>
      42
    )

    future.onComplete {
      case Success(result)    =>
        assertEquals(result, 42)
      case Failure(exception) =>
        fail(s"Unexpected failure: $exception")
    }
  }

  test("multiple withLoadingState calls should handle processing state correctly") {
    assert(!EditorState.isProcessing.now())

    val future1 = AsyncUtils.withLoadingState(() => "first")
    assert(EditorState.isProcessing.now())

    val future2 = AsyncUtils.withLoadingState(() => "second")
    assert(EditorState.isProcessing.now())

    // Both futures should complete successfully
    future1.onComplete {
      case Success(result) => assertEquals(result, "first")
      case Failure(ex)     => fail(s"Future1 failed: $ex")
    }

    future2.onComplete {
      case Success(result) => assertEquals(result, "second")
      case Failure(ex)     => fail(s"Future2 failed: $ex")
    }
  }
