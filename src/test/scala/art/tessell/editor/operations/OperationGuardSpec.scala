package art.tessell.editor.operations

import art.tessell.editor.EditorStateFixture
import art.tessell.editor.models.EditorState
import com.raquo.airstream.eventbus.EventBus
import com.raquo.airstream.ownership.ManualOwner
import munit.FunSuite

class OperationGuardSpec extends FunSuite with EditorStateFixture:

  test("ifNotProcessing(boolean) runs the thunk when isProcessing is false") {
    var ran = 0
    OperationGuard.ifNotProcessing(isProcessing = false)(ran += 1)
    assertEquals(ran, 1)
  }

  test("ifNotProcessing(boolean) drops the thunk when isProcessing is true") {
    var ran = 0
    OperationGuard.ifNotProcessing(isProcessing = true)(ran += 1)
    assertEquals(ran, 0)
  }

  test("ifNotProcessing() reads isProcessing from EditorState") {
    var ranIdle       = 0
    var ranProcessing = 0

    EditorState.uiState.update(_.copy(isProcessing = false))
    OperationGuard.ifNotProcessing(ranIdle += 1)

    EditorState.uiState.update(_.copy(isProcessing = true))
    OperationGuard.ifNotProcessing(ranProcessing += 1)

    // Reset so EditorStateFixture teardown isn't affected.
    EditorState.uiState.update(_.copy(isProcessing = false))

    assertEquals(ranIdle, 1)
    assertEquals(ranProcessing, 0)
  }

  test("gate forwards events when idle and drops them while processing") {
    val owner    = new ManualOwner
    val bus      = new EventBus[Int]
    val received = scala.collection.mutable.ListBuffer[Int]()

    EditorState.uiState.update(_.copy(isProcessing = false))
    val sub = OperationGuard.gate(bus.events).foreach(received.+=)(using owner)

    // Idle — event should pass through.
    bus.writer.onNext(1)

    // Flip to processing — event should be dropped.
    EditorState.uiState.update(_.copy(isProcessing = true))
    bus.writer.onNext(2)

    // Back to idle — event should pass through again.
    EditorState.uiState.update(_.copy(isProcessing = false))
    bus.writer.onNext(3)

    sub.kill()
    owner.killSubscriptions()

    assertEquals(received.toList, List(1, 3))
  }
