package io.github.scala_tessella.editor.utils

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.models.{AppStateSnapshot, EditorState}
import io.github.scala_tessella.editor.operations.ErrorOperations.clearError
import io.github.scala_tessella.editor.operations.MeasurementOperations

import scala.collection.mutable

object UndoManager:
  private val MAX_UNDO_DEPTH                             = 10
  private val undoStack: mutable.Stack[AppStateSnapshot] = mutable.Stack.empty
  private val redoStack: mutable.Stack[AppStateSnapshot] = mutable.Stack.empty

  // Expose read-only accessor for tests and other modules to avoid magic numbers
  def maxUndoDepth: Int = MAX_UNDO_DEPTH

  // Signals to track if undo/redo is available
  val canUndo: Var[Boolean] = Var(false)
  val canRedo: Var[Boolean] = Var(false)

  // Signals to track the number of operations that can be undone/redone
  val undoCount: Var[Int] = Var(0)
  val redoCount: Var[Int] = Var(0)

  // Save the current state to the undo stack
  def saveState(): Unit =
    if !EditorState.uiState.now().isProcessing then
      val snapshot = AppStateSnapshot.fromCurrentState

      // Don't save if the state hasn't actually changed. Structural `==` on the case class
      // compares every field (via the nested aggregate case classes).
      if undoStack.nonEmpty && undoStack.top == snapshot then
        ()
      else
        undoStack.push(snapshot)

        // A new action clears the redo history
        redoStack.clear()

        // Cap the stack at MAX_UNDO_DEPTH (drop oldest).
        while undoStack.size > MAX_UNDO_DEPTH do
          undoStack.remove(undoStack.size - 1): Unit

        updateUndoRedoSignals()

  // Undo the last operation
  def undo(): Unit =
    if !EditorState.uiState.now().isProcessing && undoStack.nonEmpty then
      // Before undoing, save the current state to the redo stack
      MeasurementOperations.clearAll()
      val currentState = AppStateSnapshot.fromCurrentState
      redoStack.push(currentState)

      val previousState = undoStack.pop()
      restoreState(previousState)
      updateUndoRedoSignals()

  // Redo the last undone operation
  def redo(): Unit =
    if !EditorState.uiState.now().isProcessing && redoStack.nonEmpty then
      // Before redoing, save the current state to the undo stack
      val currentState = AppStateSnapshot.fromCurrentState
      undoStack.push(currentState)

      val nextState = redoStack.pop()
      restoreState(nextState)
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
      val snapshot = undoStack.top
      val desc     =
        if snapshot.tessellation.currentTiling.isEmpty then "Clear tessellation"
        else "Tessellation modification"
      Some(s"Undo: $desc")
    else
      None

  def getRedoPreview: Option[String] =
    if redoStack.nonEmpty then
      Some("Redo last action")
    else
      None
