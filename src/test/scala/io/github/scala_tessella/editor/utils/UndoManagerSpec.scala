package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.tessella.IncrementalTiling
import munit.FunSuite

class UndoManagerSpec extends FunSuite {

  override def beforeEach(context: BeforeEach): Unit = {
    UndoManager.clearHistory()
    EditorState.currentTiling.set(IncrementalTiling.empty)
    EditorState.selectedPolygon.set(None)
    EditorState.selectedPerimeterEdges.set(Set.empty)
    EditorState.selectedTilingPolygons.set(Set.empty)
    EditorState.polygonColors.set(Map.empty)
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
    EditorState.currentTiling.set(TilingGenerator.createTilingFromPolygon(3).get)
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

    val newTiling = TilingGenerator.createTilingFromPolygon(4).get
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

    val newTiling = TilingGenerator.createTilingFromPolygon(4).get
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
    EditorState.currentTiling.set(TilingGenerator.createTilingFromPolygon(3).get)
    UndoManager.undo()

    UndoManager.clearHistory()

    assertEquals(UndoManager.undoCount.now(), 0)
    assertEquals(UndoManager.redoCount.now(), 0)
    assertEquals(UndoManager.canUndo.now(), false)
    assertEquals(UndoManager.canRedo.now(), false)
  }
}