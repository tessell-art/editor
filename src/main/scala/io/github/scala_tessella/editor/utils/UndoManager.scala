package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.models.{AppStateSnapshot, EditorState}
import io.github.scala_tessella.editor.operations.ErrorOperations.clearError

import com.raquo.laminar.api.L.*

import scala.collection.mutable

object UndoManager:
  private val MAX_UNDO_DEPTH = 10
  private val undoStack: mutable.Stack[AppStateSnapshot] = mutable.Stack.empty
  private val redoStack: mutable.Stack[AppStateSnapshot] = mutable.Stack.empty

  // Signals to track if undo/redo is available
  val canUndo: Var[Boolean] = Var(false)
  val canRedo: Var[Boolean] = Var(false)

  // Signals to track the number of operations that can be undone/redone
  val undoCount: Var[Int] = Var(0)
  val redoCount: Var[Int] = Var(0)

  // Save the current state to the undo stack
  def saveState(): Unit =
    if !EditorState.isProcessing.now() then
      val snapshot = AppStateSnapshot.fromCurrentState

      // Don't save if the state hasn't actually changed
      if undoStack.nonEmpty && isStateEquivalent(undoStack.top, snapshot) then
        return

      undoStack.push(snapshot)

      // A new action clears the redo history
      redoStack.clear()

      // Limit the stack size by removing the oldest elements
      if undoStack.size > MAX_UNDO_DEPTH then
        val toRemove = undoStack.size - MAX_UNDO_DEPTH
        // Convert to a temporary list to efficiently remove from the bottom (oldest entries)
        val tempBuffer = undoStack.toList
        undoStack.clear()
        undoStack.pushAll(tempBuffer.take(MAX_UNDO_DEPTH))

      updateUndoRedoSignals()

  // Undo the last operation
  def undo(): Unit =
    if !EditorState.isProcessing.now() && undoStack.nonEmpty then
      // Before undoing, save the current state to the redo stack
      val currentState = AppStateSnapshot.fromCurrentState
      redoStack.push(currentState)

      val previousState = undoStack.pop()
      restoreState(previousState)
      updateUndoRedoSignals()

  // Redo the last undone operation
  def redo(): Unit =
    if !EditorState.isProcessing.now() && redoStack.nonEmpty then
      // Before redoing, save the current state to the undo stack
      val currentState = AppStateSnapshot.fromCurrentState
      undoStack.push(currentState)

      val nextState = redoStack.pop()
      restoreState(nextState)
      updateUndoRedoSignals()

  private def isStateEquivalent(state1: AppStateSnapshot, state2: AppStateSnapshot): Boolean =
    state1.tiling == state2.tiling &&
      state1.selectedPolygon == state2.selectedPolygon &&
      state1.selectedPerimeterEdges == state2.selectedPerimeterEdges &&
      state1.selectedTilingPolygons == state2.selectedTilingPolygons &&
      state1.polygonColors == state2.polygonColors &&
      state1.fillColor == state2.fillColor &&
      state1.editorMode == state2.editorMode

  private def restoreState(snapshot: AppStateSnapshot): Unit =
    EditorState.currentTiling.set(snapshot.tiling)
    EditorState.selectedPolygon.set(snapshot.selectedPolygon)
    EditorState.selectedPerimeterEdges.set(snapshot.selectedPerimeterEdges)
    EditorState.selectedTilingPolygons.set(snapshot.selectedTilingPolygons)
    EditorState.polygonColors.set(snapshot.polygonColors)
    EditorState.fillColor.set(snapshot.fillColor)
    EditorState.editorMode.set(snapshot.editorMode)
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
      val desc = if snapshot.tiling.isEmpty then "Clear tessellation" else "Tessellation modification"
      Some(s"Undo: $desc")
    else
      None

  def getRedoPreview: Option[String] =
    if redoStack.nonEmpty then
      Some("Redo last action")
    else
      None