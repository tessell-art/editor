package art.tessell.editor.operations

import io.github.scala_tessella.dcel.{TilingDCEL, TilingError, ValidationError}
import ColorOperations.syncColorsForFaces
import art.tessell.editor.models.EditorState
import art.tessell.editor.utils.AsyncUtils

import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Try

object OperationRunner:

  /** Run a DCEL mutation that returns `Either[TilingError, T]`, converting any thrown exception into a
    * `Left(ValidationError("$errorContext: $message"))`. Use this instead of an inline `try { ... } catch {
    * case e: Exception => Left(...) }` at every `runTilingOp` call site — it's just `Try.fold` with the
    * exception-to-error formatting baked in.
    *
    * `Try` only catches `NonFatal` exceptions, so `InterruptedException` and the like still propagate as they
    * should. Plain `case e: Exception` would have caught them too, which was a latent bug at the original
    * sites.
    */
  def safely[T](errorContext: String)(op: => Either[TilingError, T]): Either[TilingError, T] =
    Try(op).fold(e => Left(ValidationError(s"$errorContext: ${e.getMessage}")), identity)

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
