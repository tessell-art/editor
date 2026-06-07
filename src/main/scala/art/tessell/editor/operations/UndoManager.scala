package art.tessell.editor.operations

import com.raquo.laminar.api.L._
import ErrorOperations.clearError
import art.tessell.editor.models.{AppStateSnapshot, EditorState}

import scala.collection.mutable

object UndoManager:

  /** What kind of action a history entry came from. Drives the *coalescing* rule: consecutive canvas-driven
    * selection clicks share a single `SelectionRun` entry, so undo lands the user at the state before the run
    * began rather than at the click in the middle of it.
    *
    * `Mutation` is everything else — tiling growth, deletions, fills, undo-aware menu commands (Select-all,
    * Deselect-all, Select-by-sides/shape), tool/MRU changes. Each `Mutation` push starts a fresh history
    * entry and ends any pending selection run.
    */
  private enum HistoryEntryKind:
    case Mutation, SelectionRun

  private case class HistoryEntry(snapshot: AppStateSnapshot, kind: HistoryEntryKind)

  private val MAX_UNDO_DEPTH                         = 50
  private val undoStack: mutable.Stack[HistoryEntry] = mutable.Stack.empty
  private val redoStack: mutable.Stack[HistoryEntry] = mutable.Stack.empty

  // Expose read-only accessor for tests and other modules to avoid magic numbers
  def maxUndoDepth: Int = MAX_UNDO_DEPTH

  // Signals to track if undo/redo is available
  val canUndo: Var[Boolean] = Var(false)
  val canRedo: Var[Boolean] = Var(false)

  // Signals to track the number of operations that can be undone/redone
  val undoCount: Var[Int] = Var(0)
  val redoCount: Var[Int] = Var(0)

  /** True when the most recent undo entry is an open selection run — i.e., the user has clicked at least one
    * selection since the last mutation. While open, further click-driven selections are no-op'd at the
    * history level so the entire run undoes as one step.
    */
  private def selectionRunOpen: Boolean =
    undoStack.headOption.exists(_.kind == HistoryEntryKind.SelectionRun)

  /** Push the current state as a new mutation history entry. Called by tiling growth/deletion, colour fills,
    * tool changes, and the undo-aware AppState wrappers. Mutation entries end any open selection run.
    *
    * Skips pushing when the latest entry already matches the current snapshot (idempotent).
    */
  def saveState(): Unit =
    if !EditorState.uiState.now().isProcessing then
      val snapshot = AppStateSnapshot.fromCurrentState
      if undoStack.nonEmpty && undoStack.top.snapshot == snapshot then ()
      else
        undoStack.push(HistoryEntry(snapshot, HistoryEntryKind.Mutation))
        redoStack.clear()
        capDepth()
        updateUndoRedoSignals()

  /** Push a selection-run entry, coalescing consecutive click-driven selections into one undo step. The first
    * click in a run pushes a new entry capturing the *pre-click* state; subsequent clicks see an open run and
    * skip the push (so undo restores the state before the run started, not the state mid-run). Always clears
    * the redo stack — a new selection cancels redo history.
    */
  def pushSelection(): Unit =
    if !EditorState.uiState.now().isProcessing then
      redoStack.clear()
      if !selectionRunOpen then
        val snapshot = AppStateSnapshot.fromCurrentState
        if undoStack.nonEmpty && undoStack.top.snapshot == snapshot then ()
        else
          undoStack.push(HistoryEntry(snapshot, HistoryEntryKind.SelectionRun))
          capDepth()
          updateUndoRedoSignals()

  // Undo the last operation
  def undo(): Unit =
    if !EditorState.uiState.now().isProcessing && undoStack.nonEmpty then
      MeasurementOperations.clearAll()
      val popped = undoStack.pop()
      // Mirror the popped entry's kind onto the redo stack so a subsequent redo restores the same
      // shape of history (a redone selection-run stays coalesced; a redone mutation stays discrete).
      redoStack.push(HistoryEntry(AppStateSnapshot.fromCurrentState, popped.kind))
      restoreState(popped.snapshot)
      updateUndoRedoSignals()

  // Redo the last undone operation
  def redo(): Unit =
    if !EditorState.uiState.now().isProcessing && redoStack.nonEmpty then
      val popped = redoStack.pop()
      undoStack.push(HistoryEntry(AppStateSnapshot.fromCurrentState, popped.kind))
      restoreState(popped.snapshot)
      updateUndoRedoSignals()

  /** Restore the snapshot atomically — one `.set`/`.update` per aggregate. Adding a new aggregate to
    * `AppStateSnapshot` adds exactly one line here; nothing can silently drift.
    */
  private def restoreState(snapshot: AppStateSnapshot): Unit =
    EditorState.tessellationState.set(snapshot.tessellation)
    EditorState.toolState.set(snapshot.tools)
    EditorState.irregularState.set(snapshot.irregular)
    EditorState.colorState.update(
      _.copy(polygonColors = snapshot.polygonColors, fillColor = snapshot.fillColor)
    )
    clearError()

  private def capDepth(): Unit =
    while undoStack.size > MAX_UNDO_DEPTH do
      undoStack.remove(undoStack.size - 1): Unit

  private def updateUndoRedoSignals(): Unit =
    canUndo.set(undoStack.nonEmpty)
    undoCount.set(undoStack.size)
    canRedo.set(redoStack.nonEmpty)
    redoCount.set(redoStack.size)

  def clearHistory(): Unit =
    undoStack.clear()
    redoStack.clear()
    updateUndoRedoSignals()

  def getUndoPreview: Option[String] =
    if undoStack.nonEmpty then
      val entry = undoStack.top
      val desc  =
        entry.kind match
          case HistoryEntryKind.SelectionRun => "Selection change"
          case HistoryEntryKind.Mutation     =>
            if entry.snapshot.tessellation.currentTiling.isEmpty then "Clear tessellation"
            else "Tessellation modification"
      Some(s"Undo: $desc")
    else
      None

  def getRedoPreview: Option[String] =
    if redoStack.nonEmpty then
      Some("Redo last action")
    else
      None
