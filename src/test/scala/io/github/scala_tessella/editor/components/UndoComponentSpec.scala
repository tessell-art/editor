package io.github.scala_tessella.editor.components

import munit.FunSuite

class UndoComponentSpec extends FunSuite:

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
