package io.github.scala_tessella.editor.interactions

import munit.FunSuite

class KeyboardEventHandlerSpec extends FunSuite:

  test("rotationDeltaForKey maps rotation keys"):
    assertEquals(KeyboardEventHandler.rotationDeltaForKey("r"), Some(15))
    assertEquals(KeyboardEventHandler.rotationDeltaForKey("R"), Some(15))
    assertEquals(KeyboardEventHandler.rotationDeltaForKey("e"), Some(-15))
    assertEquals(KeyboardEventHandler.rotationDeltaForKey("E"), Some(-15))
    assertEquals(KeyboardEventHandler.rotationDeltaForKey("x"), None)

  test("zoomFactorForKey maps zoom keys"):
    assertEquals(KeyboardEventHandler.zoomFactorForKey("+"), Some(1.1))
    assertEquals(KeyboardEventHandler.zoomFactorForKey("="), Some(1.1))
    assertEquals(KeyboardEventHandler.zoomFactorForKey("-"), Some(1.0 / 1.1))
    assertEquals(KeyboardEventHandler.zoomFactorForKey("_"), Some(1.0 / 1.1))
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
