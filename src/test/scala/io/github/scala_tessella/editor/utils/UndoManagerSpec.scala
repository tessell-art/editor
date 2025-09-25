package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.dcel.Polygon.RegularPolygon
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.TilingBuilders._
import munit.FunSuite

class UndoManagerSpec extends FunSuite with EditorStateFixture:

  override def beforeEach(context: BeforeEach): Unit = {
    super.beforeEach(context)
    UndoManager.clearHistory()
    EditorState.fillColor.set((76, 175, 80))
  }

  test("initial state has no undo or redo") {
    assertEquals(UndoManager.canUndo.now(), false)
    assertEquals(UndoManager.canRedo.now(), false)
    assertEquals(UndoManager.undoCount.now(), 0)
    assertEquals(UndoManager.redoCount.now(), 0)
  }

  test("saveState adds to undo stack and clears redo stack") {
    // Create a state and undo it to populate the redo stack
    UndoManager.saveState()
    EditorState.currentTiling.set(freshSquare())
    UndoManager.undo()

    assertEquals(UndoManager.redoCount.now(), 1)
    assertEquals(UndoManager.canRedo.now(), true)

    // Save a new state, which should clear the redo stack
    UndoManager.saveState()
    assertEquals(UndoManager.redoCount.now(), 0)
    assertEquals(UndoManager.canRedo.now(), false)
  }

  test("undo restores previous state and adds to redo stack") {
    val initialTiling = EditorState.currentTiling.now()
    UndoManager.saveState()

    val newTiling = freshSquare()
    EditorState.currentTiling.set(newTiling)

    UndoManager.undo()

    assertEquals(UndoManager.undoCount.now(), 0)
    assertEquals(UndoManager.redoCount.now(), 1)
    assertEquals(UndoManager.canRedo.now(), true)
    assertEquals(EditorState.currentTiling.now(), initialTiling)
  }

  test("redo restores undone state and adds to undo stack") {
    val initialTiling = EditorState.currentTiling.now()
    UndoManager.saveState()

    val newTiling = freshSquare()
    EditorState.currentTiling.set(newTiling)

    UndoManager.undo()
    UndoManager.redo()

    assertEquals(UndoManager.undoCount.now(), 1)
    assertEquals(UndoManager.redoCount.now(), 0)
    assertEquals(UndoManager.canRedo.now(), false)
    assertEquals(EditorState.currentTiling.now(), newTiling)
  }

  test("clearHistory clears both undo and redo stacks") {
    UndoManager.saveState()
    EditorState.currentTiling.set(freshTriangle())
    UndoManager.undo()

    UndoManager.clearHistory()

    assertEquals(UndoManager.undoCount.now(), 0)
    assertEquals(UndoManager.redoCount.now(), 0)
    assertEquals(UndoManager.canUndo.now(), false)
    assertEquals(UndoManager.canRedo.now(), false)
  }

  test("undo stack size should not exceed MAX_UNDO_DEPTH") {
    for (i <- 1 to UndoManager.maxUndoDepth + 5) {
      UndoManager.saveState()
      EditorState.selectedPolygon.set(Some(i))
    }
    assertEquals(UndoManager.undoCount.now(), UndoManager.maxUndoDepth)
  }

  test("oldest state should be discarded when MAX_UNDO_DEPTH is exceeded") {
    // This state will be pushed out of the stack
    val discardedTiling = freshTriangle()
    UndoManager.saveState()
    EditorState.currentTiling.set(discardedTiling)

    // This is the first state that should be kept
    val firstKeptTiling = freshSquare()
    UndoManager.saveState()
    EditorState.currentTiling.set(firstKeptTiling)

    // Fill the undo stack up to MAX_UNDO_DEPTH
    for (i <- 1 to UndoManager.maxUndoDepth) {
      UndoManager.saveState()
      EditorState.currentTiling.set(TilingDCEL.createRegularPolygon(RegularPolygon(i + 5)))
    }

    assertEquals(UndoManager.undoCount.now(), UndoManager.maxUndoDepth)

    // Undo all operations
    for (_ <- 1 to UndoManager.maxUndoDepth)
      UndoManager.undo()

    // The current state should be the first one that was kept
    assertEquals(EditorState.currentTiling.now(), firstKeptTiling)
    // The discarded state is not restored
    assertNotEquals(EditorState.currentTiling.now(), discardedTiling)
  }
