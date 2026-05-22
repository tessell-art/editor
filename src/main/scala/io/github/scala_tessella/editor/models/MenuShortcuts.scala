package io.github.scala_tessella.editor.models

/** Single source of truth for menu accelerator labels.
  *
  * Both [[io.github.scala_tessella.editor.components.MenuBarComponent]] (DOM menu hints) and the Tauri native
  * menu read from this table so the two can't drift; parity between this file and
  * `desktop/src-tauri/src/menu_shortcuts.rs` is enforced by the `checkMenuShortcutsParity` sbt task. Adding a
  * new labelled shortcut is one edit here; callers use [[MenuShortcuts.labelOf]].
  *
  * `primary` is the OS-conventional command modifier — Ctrl on Windows/Linux, Cmd on macOS. Label rendering
  * on the web always emits "Ctrl" (matches the pre-extraction behaviour); the Tauri menu in slice 2 renders
  * it as Cmd on macOS via its accelerator API rather than as a label string.
  */
object MenuShortcuts:

  enum MenuAction:
    case FileSave,
      EditUndo,
      EditRedo,
      EditDoubleToInfinite,
      EditDeselectAll,
      ViewFitToCanvas,
      ViewZoomIn,
      ViewZoomOut,
      ViewRotateLeft,
      ViewRotateRight

  case class Shortcut(
      key: String,
      primary: Boolean = false,
      shift: Boolean = false,
      alt: Boolean = false
  ):
    /** Renders "[Shift+][Ctrl+][Alt+]key". Shift-first ordering preserves the pre-extraction label format
      * (e.g. "Shift+Ctrl+Z" for redo), which is unusual vs. the common "Ctrl+Shift+Z" but changing it here
      * would be a user-visible regression.
      */
    def label: String =
      val parts =
        (if shift then Vector("Shift") else Vector.empty) ++
          (if primary then Vector("Ctrl") else Vector.empty) ++
          (if alt then Vector("Alt") else Vector.empty) ++
          Vector(key)
      parts.mkString("+")

  val bindings: Map[MenuAction, Shortcut] = Map(
    MenuAction.FileSave             -> Shortcut("S", primary = true),
    MenuAction.EditUndo             -> Shortcut("Z", primary = true),
    MenuAction.EditRedo             -> Shortcut("Z", primary = true, shift = true),
    MenuAction.EditDoubleToInfinite -> Shortcut("D"),
    MenuAction.EditDeselectAll      -> Shortcut("Esc"),
    MenuAction.ViewFitToCanvas      -> Shortcut("F"),
    MenuAction.ViewZoomIn           -> Shortcut("+"),
    MenuAction.ViewZoomOut          -> Shortcut("-"),
    MenuAction.ViewRotateLeft       -> Shortcut("E"),
    MenuAction.ViewRotateRight      -> Shortcut("R")
  )

  def of(action: MenuAction): Shortcut = bindings(action)

  def labelOf(action: MenuAction): String = of(action).label
