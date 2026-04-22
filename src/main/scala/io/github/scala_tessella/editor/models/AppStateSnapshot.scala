package io.github.scala_tessella.editor.models

import io.github.scala_tessella.dcel.structure.FaceId
import io.github.scala_tessella.editor.utils.ColorRGB

/** Snapshot of the subset of editor state that undo/redo operates on.
  *
  * This deliberately covers only the "app model" — domain + tool + palette state — and leaves transient UI
  * state (view transform, popups, processing flags, errors, animations, measurements) alone. Fields are
  * grouped by aggregate; two `ColorState` fields are cherry-picked because the rest of `ColorState` (picker
  * visibility, persisted user preferences, temp settings) is UI state, not app model.
  *
  * Structural equality is the intended comparison: two snapshots are equivalent iff their fields are all
  * equal. `UndoManager.saveState` skips consecutive identical snapshots using plain `==`.
  */
case class AppStateSnapshot(
    tessellation: TessellationState,
    tools: ToolState,
    irregular: IrregularState,
    polygonColors: Map[FaceId, ColorRGB],
    fillColor: ColorRGB
)

object AppStateSnapshot:
  /** Capture the current app model. Reads each aggregate `Var` once. */
  def fromCurrentState: AppStateSnapshot =
    val colors = EditorState.colorState.now()
    AppStateSnapshot(
      tessellation = EditorState.tessellationState.now(),
      tools = EditorState.toolState.now(),
      irregular = EditorState.irregularState.now(),
      polygonColors = colors.polygonColors,
      fillColor = colors.fillColor
    )
