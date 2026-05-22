package io.github.scala_tessella.editor.components

import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.operations.UndoManager
import io.github.scala_tessella.editor.utils.TilingBuilders
import munit.FunSuite
import org.scalajs.dom

class UndoComponentSpec extends FunSuite with EditorStateFixture with LaminarTestSupport:

  // --- pure helpers (P3#20 extraction; tested without mounting) ---

  test("undoTitle shows the preview when an undo is available"):
    assertEquals(UndoComponent.undoTitle(canUndo = true, preview = Some("Add hexagon")), "Add hexagon")

  test("undoTitle falls back to the bare verb when canUndo is true but no preview is set"):
    assertEquals(UndoComponent.undoTitle(canUndo = true, preview = None), "Undo")

  test("undoTitle returns the disabled-state explanation when canUndo is false"):
    assertEquals(UndoComponent.undoTitle(canUndo = false, preview = None), "No actions to undo")
    // preview is irrelevant when the action is disabled
    assertEquals(UndoComponent.undoTitle(canUndo = false, preview = Some("ignored")), "No actions to undo")

  test("redoTitle shows the preview when a redo is available"):
    assertEquals(UndoComponent.redoTitle(canRedo = true, preview = Some("Re-add square")), "Re-add square")

  test("redoTitle falls back to the bare verb when canRedo is true but no preview is set"):
    assertEquals(UndoComponent.redoTitle(canRedo = true, preview = None), "Redo")

  test("redoTitle returns the disabled-state explanation when canRedo is false"):
    assertEquals(UndoComponent.redoTitle(canRedo = false, preview = None), "No actions to redo")
    assertEquals(UndoComponent.redoTitle(canRedo = false, preview = Some("ignored")), "No actions to redo")

  // --- Laminar-mount tests ---
  // The interesting wiring here is `disabled <--` driven by two combined signals
  // (UndoManager.canUndo + EditorState.uiState.isProcessing). The pure helpers above don't
  // exercise that — these mount tests do.

  private def isDisabled(selector: String): Boolean =
    querySelector(selector).get.asInstanceOf[dom.HTMLButtonElement].disabled

  private def title(selector: String): String =
    querySelector(selector).get.getAttribute("title")

  test("with no undo history, both buttons are disabled and titled with the no-action message"):
    UndoManager.clearHistory()
    EditorState.uiState.update(_.copy(isProcessing = false))
    mount(UndoComponent.element): Unit

    assert(isDisabled(".undo-button"))
    assert(isDisabled(".redo-button"))
    assertEquals(title(".undo-button"), "No actions to undo")
    assertEquals(title(".redo-button"), "No actions to redo")

  test("after saveState, the undo button is enabled and the redo button stays disabled"):
    UndoManager.clearHistory()
    EditorState.tessellationState.update(_.copy(currentTiling = TilingBuilders.freshSquare()))
    UndoManager.saveState()
    EditorState.uiState.update(_.copy(isProcessing = false))
    mount(UndoComponent.element): Unit

    assert(!isDisabled(".undo-button"))
    assert(isDisabled(".redo-button"))

  test("after undo, the redo button becomes enabled and the redo title flips reactively"):
    UndoManager.clearHistory()
    EditorState.tessellationState.update(_.copy(currentTiling = TilingBuilders.freshTriangle()))
    UndoManager.saveState()
    EditorState.tessellationState.update(_.copy(currentTiling = TilingBuilders.freshSquare()))
    EditorState.uiState.update(_.copy(isProcessing = false))
    mount(UndoComponent.element): Unit
    assert(isDisabled(".redo-button"))

    UndoManager.undo()

    assert(!isDisabled(".redo-button"))
    // Title flipped from the disabled-state explanation to the action label or its preview.
    assertNotEquals(title(".redo-button"), "No actions to redo")

  test("isProcessing=true forces both buttons disabled even when undo/redo would otherwise be available"):
    UndoManager.clearHistory()
    EditorState.tessellationState.update(_.copy(currentTiling = TilingBuilders.freshSquare()))
    UndoManager.saveState()
    EditorState.uiState.update(_.copy(isProcessing = true))
    mount(UndoComponent.element): Unit

    assert(isDisabled(".undo-button"))
    assert(isDisabled(".redo-button"))

    // Reset so EditorStateFixture teardown isn't affected by our simulated processing flag.
    EditorState.uiState.update(_.copy(isProcessing = false))
