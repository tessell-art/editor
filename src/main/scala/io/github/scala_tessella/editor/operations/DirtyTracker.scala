package io.github.scala_tessella.editor.operations

import com.raquo.airstream.state.Var
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.file.SvgExporter

/** Tracks whether the current tiling has unsaved changes (is "dirty") and gates destructive actions (New,
  * Load, Open template, browser close) behind a confirm dialog.
  *
  * Baseline: `EditorState.fileState.now().lastSavedTiling` holds the tiling at the most recent save / load.
  * `None` means nothing has been explicitly saved yet — in that baseline an empty tiling is clean and any
  * non-empty one is dirty. After Save / Load operations call `markSaved()`, the baseline updates and
  * subsequent mutations re-mark the document as dirty.
  *
  * The "pending action" is held as a private Var so destructive callers can hand off a closure to run when
  * the user picks Discard or Save. The popup's three buttons (Cancel / Discard / Save) resolve the pending
  * state.
  */
object DirtyTracker:

  /** Closure to run after the user resolves the unsaved-changes dialog with Discard or Save. */
  private val pendingAction: Var[Option[() => Unit]] = Var(None)

  /** True when the current tiling differs from the saved baseline. */
  def isDirty: Boolean =
    val current = EditorState.tessellationState.now().currentTiling
    EditorState.fileState.now().lastSavedTiling match
      case None        => !current.isEmpty
      case Some(saved) => current != saved

  /** Marks the current tiling as the new saved baseline. Called by Save / Load operations. */
  def markSaved(): Unit =
    val current = EditorState.tessellationState.now().currentTiling
    EditorState.fileState.update(_.copy(lastSavedTiling = Some(current)))

  /** Resets the baseline to "no saved tiling" — used after New / Clear so an empty canvas reads as clean.
    */
  def resetBaseline(): Unit =
    EditorState.fileState.update(_.copy(lastSavedTiling = None))

  /** Runs `action` directly when not dirty; otherwise stores it and opens the confirm popup. */
  def confirmIfDirty(action: () => Unit): Unit =
    if isDirty then
      pendingAction.set(Some(action))
      EditorState.popupState.update(_.copy(showUnsavedConfirm = true))
    else
      action()

  // --- Confirm-dialog button handlers ---

  /** Cancel: closes the popup, drops the pending action. */
  def cancel(): Unit =
    pendingAction.set(None)
    EditorState.popupState.update(_.copy(showUnsavedConfirm = false))

  /** Discard: closes the popup, runs the pending action without saving. */
  def discardAndRun(): Unit =
    val action = pendingAction.now()
    pendingAction.set(None)
    EditorState.popupState.update(_.copy(showUnsavedConfirm = false))
    action.foreach(_())

  /** Save: triggers the SVG save, then runs the pending action — only if the save actually went through. The
    * Save / Save As paths call `markSaved()` themselves on success; we detect cancellation of the Save As
    * filename prompt by checking whether the dirty bit cleared.
    */
  def saveAndRun(): Unit =
    val hasFileName = EditorState.fileState.now().currentFileName.isDefined
    if hasFileName then SvgExporter.saveTilingToSVG()
    else SvgExporter.saveAsTilingToSVG()
    if !isDirty then
      val action = pendingAction.now()
      pendingAction.set(None)
      EditorState.popupState.update(_.copy(showUnsavedConfirm = false))
      action.foreach(_())
    // else: user cancelled the Save As prompt; keep the popup open so they can pick again.
