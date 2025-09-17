package io.github.scala_tessella.editor.operations

import OperationGuard.ifNotProcessing
import io.github.scala_tessella.editor.models.EditorState.{currentTiling, strictness}
import io.github.scala_tessella.editor.models.{EditorState, FailedPolygonDeletion, FailedPolygonPlacement}
import io.github.scala_tessella.editor.utils.PolygonNameGenerator.polygonName
import io.github.scala_tessella.editor.utils.{AsyncUtils, TilingGenerator, UndoManager}
import io.github.scala_tessella.dcel.{FaceId, TilingDCEL, TilingError, ValidationError, VertexId}
import io.github.scala_tessella.ring_seq.RingSeq.slidingO

import scala.concurrent.ExecutionContext.Implicits.global

object TessellationOperations:

  def selectPolygon(sides: Int): Unit =
    ifNotProcessing:
      EditorState.selectedPolygon.set(Some(sides))

      if currentTiling.now().isEmpty then
        UndoManager.saveState()
        TilingGenerator.createTilingFromPolygon(sides) match
          case Some(tiling) =>
            currentTiling.set(tiling)
            SelectionOperations.clearAllSelections()
          case None =>
            UndoManager.undo()
            ErrorOperations.showError(s"Failed to create tiling from $sides-sided polygon")

  def clearTiling(): Unit =
    ifNotProcessing:
      if !currentTiling.now().isEmpty then
        UndoManager.saveState()

      currentTiling.set(TilingDCEL.empty)
      EditorState.polygonColors.set(Map.empty)
      EditorState.selectedTilingPolygons.set(Set.empty)
      EditorState.selectedPerimeterEdges.set(Set.empty)

  // Attempt to delete a polygon from the tessellation
  def attemptPolygonDeletion(polygonId: String): Unit =

    val tilingBefore = currentTiling.now()
    if tilingBefore.isEmpty then
      ErrorOperations.showError("No tessellation available to modify")
    else
      val polyTag =
        if polygonId.startsWith("tiling-poly-") then polygonId.substring("tiling-poly-".length)
        else polygonId

      val op = () => {
        val tiling = currentTiling.now()
        tiling.innerFaces.find(_.id.value == polyTag) match
          case Some(face) => tiling.maybeDeleteFace(face.id)
          case None => Left(ValidationError(s"Could not find polygon with tag: $polyTag"))
      }

      OperationRunner.runTilingOp(op)(
        onSuccess = (),
        onFailure = err => {
          // Optionally create detailed deletion info here if needed
          ErrorOperations.showError(s"Cannot remove polygon: ${err.message}")
        }
      )

  // Attempt to delete a face by FaceId
  def attemptFaceDeletion(faceId: FaceId): Unit =
    val op = () => currentTiling.now().maybeDeleteFace(faceId)
    OperationRunner.runTilingOp(op)(
      onSuccess = (),
      onFailure = err => ErrorOperations.showError(s"Cannot remove polygon: ${err.message}")
    )

  // Attempt to delete a vertex by VertexId
  def attemptVertexDeletion(vertexId: VertexId): Unit =
    val op = () => currentTiling.now().maybeDeleteVertex(vertexId)
    OperationRunner.runTilingOp(op)(
      onSuccess = (),
      onFailure = err => ErrorOperations.showError(s"Cannot remove vertex: ${err.message}")
    )

  // Attempt to delete an edge by endpoints
  def attemptEdgeDeletion(startVertexId: VertexId, endVertexId: VertexId): Unit =
    val op = () => currentTiling.now().maybeDeleteEdge(startVertexId, endVertexId)
    OperationRunner.runTilingOp(op)(
      onSuccess = (),
      onFailure = err => ErrorOperations.showError(s"Cannot remove edge: ${err.message}")
    )

  // Handle perimeter edge click with polygon growth
  def attemptPolygonAddition(edgeId: String, edgeIndex: Int): Unit =
    (currentTiling.now(), EditorState.selectedPolygon.now()) match
      case (tiling, _) if tiling.isEmpty =>
        ErrorOperations.showError("No tiling available to grow")
      case (tiling, Some(polygonSides)) =>
        val op = () =>
          try
            val perimeterEdges = tiling.boundaryVertices.toOption.get.map(_.id).slidingO(2).toList
            if edgeIndex < perimeterEdges.length then
              val selectedEdge = perimeterEdges(edgeIndex)
              tiling.maybeAddRegularPolygonToBoundary(selectedEdge.head, polygonSides)
            else
              Left(ValidationError("Invalid edge index"))
          catch
            case e: Exception => Left(ValidationError(s"Error growing edge: ${e.getMessage}"))

        OperationRunner.runTilingOp(op)(
          onSuccess = {
            EditorState.selectedPerimeterEdges.set(Set.empty)
          },
          onFailure = err => {
            val perimeterEdges = tiling.boundaryVertices.toOption.get.map(_.id).slidingO(2).toList
            if edgeIndex < perimeterEdges.length then
              val selectedEdge = perimeterEdges(edgeIndex)
              val placement = FailedPolygonPlacement(edgeIndex, polygonSides, (selectedEdge(0), selectedEdge(1)), tiling)
              val truncated = err.message
              ErrorOperations.showError(
                s"Growing ${polygonName(polygonSides)}s on this perimeter edge is invalid. Switch Validation OFF to proceed. $truncated",
                Some(placement)
              )
            else
              ErrorOperations.showError(err.message)
          }
        )
      case (_, None) =>
        ()

  // Helper: try to find the inner face that contains this directed edge; if not found, None
  private def findFaceContainingEdge(tiling: TilingDCEL, v1: VertexId, v2: VertexId): Option[FaceId] =
    // We look for a face whose boundary contains the directed edge (v1 -> v2).
    // If DCEL provides halfEdges on face with next pointers, prefer that; here we rely on vertex order on the face.
    tiling.innerFaces.find { face =>
      val ids = face.getVertices.toOption.get.map(_.id).toVector
      ids.slidingO(2).exists(pair => pair(0) == v1 && pair(1) == v2)
    }.map(_.id)

  def attemptPolygonInsertion(startVertexId: VertexId, endVertexId: VertexId): Unit =
    (currentTiling.now(), EditorState.selectedPolygon.now()) match
      case (tiling, _) if tiling.isEmpty =>
        ErrorOperations.showError("No tiling available for insertion")
      case (tiling, Some(polygonSides)) =>
        val op = () =>
          try tiling.maybeAddRegularPolygon(startVertexId, endVertexId, polygonSides)
          catch
            case e: Exception => Left(ValidationError(s"Error inserting polygon: ${e.getMessage}"))
  
        OperationRunner.runTilingOp(op)(
          onSuccess = {
            EditorState.selectedPerimeterEdges.set(Set.empty)
          },
          onFailure = error => {
            val curr = currentTiling.now()
            val maybeFaceId = findFaceContainingEdge(curr, startVertexId, endVertexId)
            val placementOpt =
              Some(
                FailedPolygonPlacement(
                  edgeIndex = 0, // not needed for interior wireframe
                  polygonSides = polygonSides,
                  edge = (startVertexId, endVertexId),
                  tiling = curr,
                  intoFace = maybeFaceId
                )
              )
            ErrorOperations.showError(s"Cannot insert polygon: ${error.message}", placement = placementOpt)
          }
        )
      case (_, None) =>
        ()
