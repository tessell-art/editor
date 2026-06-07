package art.tessell.editor.interactions

import art.tessell.editor.EditorStateFixture
import art.tessell.editor.models.{EditorConfig, EditorState}
import art.tessell.editor.operations.UndoManager
import art.tessell.editor.utils.TilingBuilders
import io.github.scala_tessella.dcel.TilingDCEL
import io.github.scala_tessella.dcel.structure.FaceId
import munit.FunSuite
import org.scalajs.dom

import scala.scalajs.js

class KeyboardEventHandlerSpec extends FunSuite with EditorStateFixture:

  // --- pure helpers ---

  test("rotationDeltaForKey maps rotation keys"):
    assertEquals(KeyboardEventHandler.rotationDeltaForKey("e"), Some(15))
    assertEquals(KeyboardEventHandler.rotationDeltaForKey("E"), Some(15))
    assertEquals(KeyboardEventHandler.rotationDeltaForKey("q"), Some(-15))
    assertEquals(KeyboardEventHandler.rotationDeltaForKey("Q"), Some(-15))
    assertEquals(KeyboardEventHandler.rotationDeltaForKey("x"), None)

  test("zoomFactorForKey maps zoom keys"):
    assertEquals(KeyboardEventHandler.zoomFactorForKey("+"), Some(EditorConfig.keyboardZoomFactor))
    assertEquals(KeyboardEventHandler.zoomFactorForKey("="), Some(EditorConfig.keyboardZoomFactor))
    assertEquals(KeyboardEventHandler.zoomFactorForKey("-"), Some(1.0 / EditorConfig.keyboardZoomFactor))
    assertEquals(KeyboardEventHandler.zoomFactorForKey("_"), Some(1.0 / EditorConfig.keyboardZoomFactor))
    assertEquals(KeyboardEventHandler.zoomFactorForKey("z"), None)

  test("undo/redo/save shortcut helpers detect modifiers"):
    assert(KeyboardEventHandler.isUndoShortcut("z", primary = true, shift = false))
    assert(!KeyboardEventHandler.isUndoShortcut("z", primary = true, shift = true))
    assert(!KeyboardEventHandler.isUndoShortcut("z", primary = false, shift = false))

    assert(KeyboardEventHandler.isRedoShortcut("Z", primary = true, shift = true))
    assert(KeyboardEventHandler.isRedoShortcut("z", primary = true, shift = true))
    assert(!KeyboardEventHandler.isRedoShortcut("z", primary = true, shift = false))

    assert(KeyboardEventHandler.isSaveShortcut("s", primary = true))
    assert(!KeyboardEventHandler.isSaveShortcut("s", primary = false))

  // --- handleKeyDown (synthetic-event dispatch in JSDOM) ---

  private def keyEvent(
      key: String,
      ctrl: Boolean = false,
      meta: Boolean = false,
      shift: Boolean = false
  ): dom.KeyboardEvent =
    val init = js.Dynamic
      .literal(
        key = key,
        ctrlKey = ctrl,
        metaKey = meta,
        shiftKey = shift,
        bubbles = true,
        cancelable = true
      )
      .asInstanceOf[dom.KeyboardEventInit]
    new dom.KeyboardEvent("keydown", init)

  // Set both ctrl and meta so the test passes regardless of JSDOM's reported platform.
  private def primaryKeyEvent(key: String, shift: Boolean = false): dom.KeyboardEvent =
    keyEvent(key, ctrl = true, meta = true, shift = shift)

  test("handleKeyDown Escape clears all selections") {
    val tiling = TilingBuilders.freshSquare()
    EditorState.tessellationState.update(_.copy(
      currentTiling = tiling,
      selectedPerimeterEdges = Set("edge-1", "edge-2"),
      selectedTilingPolygons = Set(FaceId(1))
    ))

    KeyboardEventHandler.handleKeyDown(keyEvent("Escape"), tiling, hasFileName = false)

    assertEquals(EditorState.tessellationState.now().selectedPerimeterEdges, Set.empty[String])
    assertEquals(EditorState.tessellationState.now().selectedTilingPolygons, Set.empty[FaceId])
  }

  test("handleKeyDown undo shortcut (primary+z) restores the previous tiling snapshot") {
    val triangle = TilingBuilders.freshTriangle()
    val square   = TilingBuilders.freshSquare()

    EditorState.tessellationState.update(_.copy(currentTiling = triangle))
    UndoManager.clearHistory()
    UndoManager.saveState()
    EditorState.tessellationState.update(_.copy(currentTiling = square))
    assertEquals(UndoManager.undoCount.now(), 1)

    KeyboardEventHandler.handleKeyDown(primaryKeyEvent("z"), square, hasFileName = false)

    assertEquals(EditorState.tessellationState.now().currentTiling, triangle)
    assertEquals(UndoManager.redoCount.now(), 1)
  }

  test("handleKeyDown redo shortcut (primary+shift+Z) reapplies the undone tiling") {
    val triangle = TilingBuilders.freshTriangle()
    val square   = TilingBuilders.freshSquare()

    EditorState.tessellationState.update(_.copy(currentTiling = triangle))
    UndoManager.clearHistory()
    UndoManager.saveState()
    EditorState.tessellationState.update(_.copy(currentTiling = square))
    UndoManager.undo()
    assertEquals(EditorState.tessellationState.now().currentTiling, triangle)
    assertEquals(UndoManager.redoCount.now(), 1)

    KeyboardEventHandler.handleKeyDown(primaryKeyEvent("Z", shift = true), triangle, hasFileName = false)

    assertEquals(EditorState.tessellationState.now().currentTiling, square)
    assertEquals(UndoManager.redoCount.now(), 0)
  }

  test("handleKeyDown ignores events whose target is an <input> element") {
    val tiling = TilingBuilders.freshSquare()
    EditorState.tessellationState.update(_.copy(
      currentTiling = tiling,
      selectedPerimeterEdges = Set("edge-1")
    ))

    val input = dom.document.createElement("input")
    dom.document.body.appendChild(input): Unit
    try
      val event = keyEvent("Escape")
      // Dispatching against the input sets event.target to the input, which isTargetInput rejects.
      input.dispatchEvent(event): Unit

      KeyboardEventHandler.handleKeyDown(event, tiling, hasFileName = false)

      // Selection still present — the keydown was treated as user typing in an input.
      assertEquals(EditorState.tessellationState.now().selectedPerimeterEdges, Set("edge-1"))
    finally
      dom.document.body.removeChild(input): Unit
  }

  test("handleKeyDown is a no-op for unmapped keys (e.g. 'x')") {
    val tiling = TilingBuilders.freshSquare()
    EditorState.tessellationState.update(_.copy(
      currentTiling = tiling,
      selectedPerimeterEdges = Set("edge-1")
    ))
    UndoManager.clearHistory()

    KeyboardEventHandler.handleKeyDown(keyEvent("x"), tiling, hasFileName = false)

    assertEquals(EditorState.tessellationState.now().currentTiling, tiling)
    assertEquals(EditorState.tessellationState.now().selectedPerimeterEdges, Set("edge-1"))
    assertEquals(UndoManager.undoCount.now(), 0)
  }

  test("handleKeyDown 'd' on empty tiling does nothing") {
    val empty = TilingDCEL.empty
    EditorState.tessellationState.update(_.copy(currentTiling = empty))
    UndoManager.clearHistory()

    KeyboardEventHandler.handleKeyDown(keyEvent("d"), empty, hasFileName = false)

    // No state change because the empty-tiling guard short-circuits before AppState.doubleTiling.
    assert(EditorState.tessellationState.now().currentTiling.isEmpty)
    assertEquals(UndoManager.undoCount.now(), 0)
  }
