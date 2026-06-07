package art.tessell.editor.models

import MenuShortcuts.{MenuAction, Shortcut}
import munit.FunSuite

class MenuShortcutsSpec extends FunSuite:

  test("no-modifier shortcut renders as just the key") {
    assertEquals(Shortcut("Esc").label, "Esc")
    assertEquals(Shortcut("F").label, "F")
    assertEquals(Shortcut("+").label, "+")
  }

  test("primary-only shortcut renders as Ctrl+key on the web") {
    assertEquals(Shortcut("S", primary = true).label, "Ctrl+S")
    assertEquals(Shortcut("Z", primary = true).label, "Ctrl+Z")
  }

  test("primary+shift shortcut renders Shift before Ctrl (preserved pre-extraction ordering)") {
    assertEquals(Shortcut("Z", primary = true, shift = true).label, "Shift+Ctrl+Z")
  }

  test("primary+shift+alt renders in Shift+Ctrl+Alt+key order") {
    assertEquals(
      Shortcut("K", primary = true, shift = true, alt = true).label,
      "Shift+Ctrl+Alt+K"
    )
  }

  test("every MenuAction has a binding") {
    MenuAction.values.foreach { action =>

      assert(MenuShortcuts.bindings.contains(action), s"missing binding for $action")
    }
  }

  test("labelOf preserves the exact strings that MenuBarComponent used to hard-code") {
    assertEquals(MenuShortcuts.labelOf(MenuAction.FileSave), "Ctrl+S")
    assertEquals(MenuShortcuts.labelOf(MenuAction.EditUndo), "Ctrl+Z")
    assertEquals(MenuShortcuts.labelOf(MenuAction.EditRedo), "Shift+Ctrl+Z")
    assertEquals(MenuShortcuts.labelOf(MenuAction.EditDeselectAll), "Esc")
    assertEquals(MenuShortcuts.labelOf(MenuAction.ViewFitToCanvas), "F")
    assertEquals(MenuShortcuts.labelOf(MenuAction.ViewZoomIn), "+")
    assertEquals(MenuShortcuts.labelOf(MenuAction.ViewZoomOut), "-")
    assertEquals(MenuShortcuts.labelOf(MenuAction.ViewRotateLeft), "Q")
    assertEquals(MenuShortcuts.labelOf(MenuAction.ViewRotateRight), "E")
    assertEquals(MenuShortcuts.labelOf(MenuAction.EditAddCopyTranslate), "T")
    assertEquals(MenuShortcuts.labelOf(MenuAction.EditAddCopyRotate), "R")
    assertEquals(MenuShortcuts.labelOf(MenuAction.EditAddCopyReflect), "Y")
  }
