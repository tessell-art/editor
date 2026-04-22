package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.dcel.{TilingDCEL, TilingError}
import io.github.scala_tessella.editor.models.EditorState
import io.github.scala_tessella.editor.utils.{AsyncUtils, UndoManager}
import io.github.scala_tessella.editor.operations.ColorOperations.syncColorsForFaces

import scala.concurrent.ExecutionContext.Implicits.global

object OperationRunner:

  /** Run an async DCEL mutation that returns Either[TilingError, TilingDCEL]
    *   - Shows a loading state
    *   - Saves undo state only if the new tiling differs from the current one
    *   - Updates the current tiling on success and clears error
    *   - Calls onSuccess / onFailure hooks for caller-specific behaviors
    */
  def runTilingOp(
      op: () => Either[TilingError, TilingDCEL]
  )(
      onSuccess: => Unit = (),
      onFailure: TilingError => Unit
  ): Unit =
    AsyncUtils.withLoadingState(op).foreach {
      case Right(newTiling) =>
        val current = EditorState.tessellationState.now().currentTiling
        if newTiling != current then
          // Save the pre-change snapshot only if the result will differ
          UndoManager.saveState()
          val newFaces = newTiling.innerFaces.map(_.id).toSet
          EditorState.tessellationState.update(_.copy(currentTiling = newTiling))
          syncColorsForFaces(newFaces, EditorState.colorState.now().fillColor)
        // Clear error and allow caller extras (e.g., clearing selections)
        ErrorOperations.clearError()
        onSuccess
      case Left(err)        =>
        onFailure(err)
    }
