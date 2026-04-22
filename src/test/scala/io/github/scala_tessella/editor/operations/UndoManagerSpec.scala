package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.geometry.RegularPolygon
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.editor.EditorStateFixture
import io.github.scala_tessella.editor.models.{AppStateSnapshot, EditorState}
import io.github.scala_tessella.editor.models.Tool
import io.github.scala_tessella.editor.utils.ColorRGB
import io.github.scala_tessella.editor.utils.TilingBuilders.*
import munit.FunSuite

class UndoManagerSpec extends FunSuite with EditorStateFixture:

  override def beforeEach(context: BeforeEach): Unit = {
    super.beforeEach(context)
    UndoManager.clearHistory()
    EditorState.colorState.update(_.copy(fillColor = ColorRGB(76, 175, 80)))
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
    EditorState.tessellationState.update(_.copy(currentTiling = freshSquare()))
    UndoManager.undo()

    assertEquals(UndoManager.redoCount.now(), 1)
    assertEquals(UndoManager.canRedo.now(), true)

    // Save a new state, which should clear the redo stack
    UndoManager.saveState()
    assertEquals(UndoManager.redoCount.now(), 0)
    assertEquals(UndoManager.canRedo.now(), false)
  }

  test("undo restores previous state and adds to redo stack") {
    val initialTiling = EditorState.tessellationState.now().currentTiling
    UndoManager.saveState()

    val newTiling = freshSquare()
    EditorState.tessellationState.update(_.copy(currentTiling = newTiling))

    UndoManager.undo()

    assertEquals(UndoManager.undoCount.now(), 0)
    assertEquals(UndoManager.redoCount.now(), 1)
    assertEquals(UndoManager.canRedo.now(), true)
    assertEquals(EditorState.tessellationState.now().currentTiling, initialTiling)
  }

  test("redo restores undone state and adds to undo stack") {
    val initialTiling = EditorState.tessellationState.now().currentTiling
    UndoManager.saveState()

    val newTiling = freshSquare()
    EditorState.tessellationState.update(_.copy(currentTiling = newTiling))

    UndoManager.undo()
    UndoManager.redo()

    assertEquals(UndoManager.undoCount.now(), 1)
    assertEquals(UndoManager.redoCount.now(), 0)
    assertEquals(UndoManager.canRedo.now(), false)
    assertEquals(EditorState.tessellationState.now().currentTiling, newTiling)
  }

  test("clearHistory clears both undo and redo stacks") {
    UndoManager.saveState()
    EditorState.tessellationState.update(_.copy(currentTiling = freshTriangle()))
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
      EditorState.toolState.update(_.copy(selectedPolygon = Some(i)))
    }
    assertEquals(UndoManager.undoCount.now(), UndoManager.maxUndoDepth)
  }

  test("oldest state should be discarded when MAX_UNDO_DEPTH is exceeded") {
    // This state will be pushed out of the stack
    val discardedTiling = freshTriangle()
    UndoManager.saveState()
    EditorState.tessellationState.update(_.copy(currentTiling = discardedTiling))

    // This is the first state that should be kept
    val firstKeptTiling = freshSquare()
    UndoManager.saveState()
    EditorState.tessellationState.update(_.copy(currentTiling = firstKeptTiling))

    // Fill the undo stack up to MAX_UNDO_DEPTH
    for (i <- 1 to UndoManager.maxUndoDepth) {
      UndoManager.saveState()
      EditorState.tessellationState.update(_.copy(currentTiling =
        TilingDCEL.createRegularPolygon(RegularPolygon(i + 5))
      ))
    }

    assertEquals(UndoManager.undoCount.now(), UndoManager.maxUndoDepth)

    // Undo all operations
    for (_ <- 1 to UndoManager.maxUndoDepth)
      UndoManager.undo()

    // The current state should be the first one that was kept
    assertEquals(EditorState.tessellationState.now().currentTiling, firstKeptTiling)
    // The discarded state is not restored
    assertNotEquals(EditorState.tessellationState.now().currentTiling, discardedTiling)
  }

  private def reset(): Unit =
    UndoManager.clearHistory()
    // Ensure processing flag is false
    EditorState.uiState.update(_.copy(isProcessing = false))

  test("maxUndoDepth exposes internal limit") {
    assert(UndoManager.maxUndoDepth > 0)
  }

  test("saveState does not push equivalent consecutive snapshots") {
    reset()
    val s1         = AppStateSnapshot.fromCurrentState
    UndoManager.saveState()
    val undoCount1 = UndoManager.undoCount.now()
    // No changes -> should not add a duplicate
    UndoManager.saveState()
    val undoCount2 = UndoManager.undoCount.now()
    assert(undoCount2 == undoCount1)
  }

  test("undo/redo availability signals update and redo clears on new save") {
    reset()
    UndoManager.saveState()
    UndoManager.saveState() // make at least one step available
    assert(UndoManager.canUndo.now())
    UndoManager.undo()
    assert(UndoManager.canRedo.now())

    // Pushing new state clears redo
    UndoManager.saveState()
    assert(!UndoManager.canRedo.now())
  }

  test("undo stack is capped to max depth") {
    reset()
    val cap = UndoManager.maxUndoDepth
    (0 until (cap + 5)).foreach(_ => UndoManager.saveState())
    assert(UndoManager.undoCount.now() <= cap)
  }

  test("getUndoPreview and getRedoPreview reflect stack state") {
    reset()
    assert(UndoManager.getUndoPreview.isEmpty)
    assert(UndoManager.getRedoPreview.isEmpty)

    UndoManager.saveState()
    assert(UndoManager.getUndoPreview.exists(_.startsWith("Undo:")))
    UndoManager.undo()
    assert(UndoManager.getRedoPreview.nonEmpty)
  }

  test("undo/redo should restore tool state and selections") {
    reset()
    EditorState.tessellationState.update(_.copy(currentTiling = freshSquare()))
    val faceId = EditorState.tessellationState.now().currentTiling.innerFaces.head.id

    EditorState.toolState.update(_.copy(activeTool = None))
    EditorState.tessellationState.update(_.copy(selectedTilingPolygons = Set(faceId)))
    UndoManager.saveState()

    EditorState.toolState.update(_.copy(activeTool = Some(Tool.ColorPicker)))
    EditorState.tessellationState.update(_.copy(selectedTilingPolygons = Set.empty))

    UndoManager.undo()
    assertEquals(EditorState.toolState.now().activeTool, None)
    assertEquals(EditorState.tessellationState.now().selectedTilingPolygons, Set(faceId))

    UndoManager.redo()
    assertEquals(EditorState.toolState.now().activeTool, Some(Tool.ColorPicker))
    assertEquals(EditorState.tessellationState.now().selectedTilingPolygons, Set.empty)
  }
