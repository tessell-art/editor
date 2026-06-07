package art.tessell.editor.operations

import art.tessell.editor.models.EditorState
import io.github.scala_tessella.dcel.structure.{FaceId, VertexId}

/** Tiling-deletion operations. Each method is a thin wrapper around the corresponding DCEL `maybeDelete…`
  * call, routed through [[OperationRunner]] so success/failure handling and undo snapshotting are
  * centralised.
  *
  * `attempt…` naming: these return `Unit` — on validation failure an error message is surfaced via
  * [[ErrorOperations]], not thrown. Throwing would couple `operations` to caller-side error handling and
  * defeat the one-way `components/interactions → operations → models` flow the build enforces.
  */
object DeletionOperations:

  /** Attempt to delete a face by `FaceId` (DCEL-native, id-stable). */
  def attemptFaceDeletion(faceId: FaceId): Unit =
    val op = () => EditorState.tessellationState.now().currentTiling.maybeDeleteFace(faceId)
    OperationRunner.runTilingOp(op)(
      onSuccess = SymmetryOperations.clearOverlays(),
      onFailure = err => ErrorOperations.showError(s"Cannot remove polygon: ${err.message}")
    )

  /** Attempt to delete a vertex by `VertexId`. */
  def attemptVertexDeletion(vertexId: VertexId): Unit =
    val op = () => EditorState.tessellationState.now().currentTiling.maybeDeleteVertex(vertexId)
    OperationRunner.runTilingOp(op)(
      onSuccess = SymmetryOperations.clearOverlays(),
      onFailure = err => ErrorOperations.showError(s"Cannot remove vertex: ${err.message}")
    )

  /** Attempt to delete an edge by its two vertex endpoints. */
  def attemptEdgeDeletion(startVertexId: VertexId, endVertexId: VertexId): Unit =
    val op =
      () => EditorState.tessellationState.now().currentTiling.maybeDeleteEdge(startVertexId, endVertexId)
    OperationRunner.runTilingOp(op)(
      onSuccess = SymmetryOperations.clearOverlays(),
      onFailure = err => ErrorOperations.showError(s"Cannot remove edge: ${err.message}")
    )
