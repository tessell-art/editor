package io.github.scala_tessella.editor.utils

import com.raquo.laminar.api.L._
import io.github.scala_tessella.editor.models.{AppStateSnapshot, EditorState, TessellationState, ToolState}
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

      // Don't save if the state hasn't actually changed
      if undoStack.nonEmpty && isStateEquivalent(undoStack.top, snapshot) then
        ()
      else
        undoStack.push(snapshot)

        // A new action clears the redo history
        redoStack.clear()

        // Limit the stack size to MAX_UNDO_DEPTH
        if undoStack.size > MAX_UNDO_DEPTH then
          val tempStack = mutable.Stack[AppStateSnapshot]()
          for (_ <- 0 until MAX_UNDO_DEPTH)
            if undoStack.nonEmpty then
              tempStack.push(undoStack.pop())
          undoStack.clear()
          while tempStack.nonEmpty do
            undoStack.push(tempStack.pop())

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

  private def isStateEquivalent(state1: AppStateSnapshot, state2: AppStateSnapshot): Boolean =
    state1.tiling == state2.tiling &&
      state1.selectedPolygon == state2.selectedPolygon &&
      state1.selectedPerimeterEdges == state2.selectedPerimeterEdges &&
      state1.selectedTilingPolygons == state2.selectedTilingPolygons &&
      state1.polygonColors == state2.polygonColors &&
      state1.fillColor == state2.fillColor &&
      state1.editorMode == state2.editorMode &&
      state1.activeTool == state2.activeTool &&
      state1.recentIrregularPolygon == state2.recentIrregularPolygon &&
      state1.isIrregularSelected == state2.isIrregularSelected

  private def restoreState(snapshot: AppStateSnapshot): Unit =
    EditorState.tessellationState.set(
      TessellationState(
        currentTiling = snapshot.tiling,
        selectedPerimeterEdges = snapshot.selectedPerimeterEdges,
        selectedTilingPolygons = snapshot.selectedTilingPolygons
      )
    )
    EditorState.colorState.update(_.copy(polygonColors = snapshot.polygonColors))
    EditorState.colorState.update(_.copy(fillColor = snapshot.fillColor))
    EditorState.toolState.set(
      ToolState(
        editorMode = snapshot.editorMode,
        activeTool = snapshot.activeTool,
        selectedPolygon = snapshot.selectedPolygon
      )
    )
    EditorState.recentIrregularPolygon.set(snapshot.recentIrregularPolygon)
    EditorState.isIrregularSelected.set(snapshot.isIrregularSelected)
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
      val desc     = if snapshot.tiling.isEmpty then "Clear tessellation" else "Tessellation modification"
      Some(s"Undo: $desc")
    else
      None

  def getRedoPreview: Option[String] =
    if redoStack.nonEmpty then
      Some("Redo last action")
    else
      None
