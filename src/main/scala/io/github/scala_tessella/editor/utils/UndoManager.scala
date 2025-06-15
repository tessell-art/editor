package io.github.scala_tessella.editor.utils

import io.github.scala_tessella.editor.models.{AppStateSnapshot, EditorState}
import io.github.scala_tessella.editor.operations.ErrorOperations.clearError
import com.raquo.laminar.api.L.{Var, Signal}
import scala.collection.mutable

object UndoManager:
  private val MAX_UNDO_DEPTH = 10
  private val undoStack: mutable.Stack[AppStateSnapshot] = mutable.Stack.empty
  
  // Signal to track if undo is available
  val canUndo: Var[Boolean] = Var(false)
  
  // Signal to track the number of operations that can be undone
  val undoCount: Var[Int] = Var(0)

  // Save the current state to the undo stack
  def saveState(): Unit =
    if !EditorState.isProcessing.now() then
      val snapshot = AppStateSnapshot.fromCurrentState
      
      // Don't save if the state hasn't actually changed
      if undoStack.nonEmpty && isStateEquivalent(undoStack.top, snapshot) then
        return
      
      undoStack.push(snapshot)
      
      // Limit the stack size to MAX_UNDO_DEPTH
      if undoStack.size > MAX_UNDO_DEPTH then
        // Remove the oldest state (bottom of stack)
        val tempStack = mutable.Stack[AppStateSnapshot]()
        // Keep only the most recent MAX_UNDO_DEPTH states
        for (_ <- 0 until MAX_UNDO_DEPTH) {
          if undoStack.nonEmpty then
            tempStack.push(undoStack.pop())
        }
        undoStack.clear()
        while tempStack.nonEmpty do
          undoStack.push(tempStack.pop())
      
      updateUndoSignals()

  // Undo the last operation
  def undo(): Unit =
    if !EditorState.isProcessing.now() && undoStack.nonEmpty then
      val previousState = undoStack.pop()
      restoreState(previousState)
      updateUndoSignals()

  // Check if two states are equivalent (to avoid saving duplicate states)
  private def isStateEquivalent(state1: AppStateSnapshot, state2: AppStateSnapshot): Boolean =
    state1.tiling == state2.tiling &&
    state1.selectedPolygon == state2.selectedPolygon &&
    state1.selectedPerimeterEdges == state2.selectedPerimeterEdges &&
    state1.selectedTilingPolygons == state2.selectedTilingPolygons &&
    state1.polygonColors == state2.polygonColors &&
    state1.fillColor == state2.fillColor &&
    state1.editorMode == state2.editorMode

  // Restore a previous state
  private def restoreState(snapshot: AppStateSnapshot): Unit =
    EditorState.currentTiling.set(snapshot.tiling)
    EditorState.selectedPolygon.set(snapshot.selectedPolygon)
    EditorState.selectedPerimeterEdges.set(snapshot.selectedPerimeterEdges)
    EditorState.selectedTilingPolygons.set(snapshot.selectedTilingPolygons)
    EditorState.polygonColors.set(snapshot.polygonColors)
    EditorState.fillColor.set(snapshot.fillColor)
    EditorState.editorMode.set(snapshot.editorMode)
    
    // Clear any error states when undoing
    clearError()

  // Update the signals that track undo availability
  private def updateUndoSignals(): Unit =
    canUndo.set(undoStack.nonEmpty)
    undoCount.set(undoStack.size)

  // Clear the undo history
  def clearHistory(): Unit =
    undoStack.clear()
    updateUndoSignals()

  // Get a preview of what would be undone (for UI purposes)
  def getUndoPreview: Option[String] =
    if undoStack.nonEmpty then
      val snapshot = undoStack.top
      val desc = snapshot.tiling match
        case Some(_) => "Tessellation modification"
        case None => "Clear tessellation"
      Some(s"Undo: $desc")
    else
      None