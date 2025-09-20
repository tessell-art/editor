package io.github.scala_tessella.editor.operations

import OperationGuard.ifNotProcessing
import io.github.scala_tessella.dcel.Polygon.RegularPolygon
import io.github.scala_tessella.editor.models.EditorState.currentTiling
import io.github.scala_tessella.editor.models.{EditorState, FailedPolygonDeletion, FailedPolygonPlacement}
import io.github.scala_tessella.editor.utils.PolygonNameGenerator.polygonName
import io.github.scala_tessella.editor.utils.{Logger, UndoManager}
import io.github.scala_tessella.dcel.{FaceId, TilingDCEL, TilingError, ValidationError, VertexId}
import io.github.scala_tessella.ring_seq.RingSeq.slidingO

object TessellationOperations:

  def selectPolygon(sides: Int): Unit =
    ifNotProcessing:
      // Selecting a regular polygon deselects the irregular
      EditorState.isIrregularSelected.set(false)
      EditorState.selectedPolygon.set(Some(sides))

      if currentTiling.now().isEmpty then
        UndoManager.saveState()
        TilingDCEL.createRegularPolygon(sides).toOption match
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

  /** Select the irregular polygon in the palette (deselect regular if any). */
  def selectIrregularInPalette(): Unit =
    ifNotProcessing:
      if EditorState.recentIrregularPolygon.now().isDefined then
        EditorState.selectedPolygon.set(None)
        EditorState.isIrregularSelected.set(true)

  /** If the tiling is empty and a recent irregular exists, initialize the tiling with it. */
  def initializeWithIrregularIfEmpty(): Unit =
    ifNotProcessing:
      if currentTiling.now().isEmpty then
        EditorState.recentIrregularPolygon.now() match
          case Some(angles) =>
            UndoManager.saveState()
            TilingDCEL.createSimplePolygon(angles.toList).toOption match
              case Some(tiling) =>
                currentTiling.set(tiling)
                SelectionOperations.clearAllSelections()
              case None =>
                UndoManager.undo()
                ErrorOperations.showError("Failed to create tiling from irregular polygon")
          case None => ()

  // Attempt to delete a face by FaceId (stable, DCEL-native)
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

  // Attempt to delete an edge by endpoints (stable VertexId pair)
  def attemptEdgeDeletion(startVertexId: VertexId, endVertexId: VertexId): Unit =
    val op = () => currentTiling.now().maybeDeleteEdge(startVertexId, endVertexId)
    OperationRunner.runTilingOp(op)(
      onSuccess = (),
      onFailure = err => ErrorOperations.showError(s"Cannot remove edge: ${err.message}")
    )

  // Handle perimeter edge click with polygon growth
  def attemptPolygonAddition(edgeId: String, edgeIndex: Int): Unit =
    (currentTiling.now(), EditorState.selectedPolygon.now(), EditorState.isIrregularSelected.now()) match
      case (tiling, _, _) if tiling.isEmpty =>
        ErrorOperations.showError("No tiling available to grow")
      case (_, None, false) =>
        Logger.warn("Both regular polygon and irregular polygon unselected")
      case (_, Some(_), true) =>
        Logger.error("Should not happen: both regular polygon and irregular polygon selected")
      case (tiling, maybeSides, _) =>
        val perimeterEdges = tiling.boundaryVertices.toOption.get.map(_.id).slidingO(2).toList
        val op = () =>
          try
            if edgeIndex < perimeterEdges.length then
              val selectedEdge = perimeterEdges(edgeIndex)
              if maybeSides.isDefined then
                tiling.maybeAddRegularPolygonToBoundary(selectedEdge.head, maybeSides.get)
              else
                val angles = EditorState.recentIrregularPolygon.now().get.toList
                tiling.maybeAddSimplePolygonToBoundary(selectedEdge.head, angles)
            else
              Left(ValidationError("Invalid edge index"))
          catch
            case e: Exception => Left(ValidationError(s"Error growing edge: ${e.getMessage}"))

        OperationRunner.runTilingOp(op)(
          onSuccess = {
            EditorState.selectedPerimeterEdges.set(Set.empty)
          },
          onFailure = err => {
            if edgeIndex < perimeterEdges.length then
              val selectedEdge = perimeterEdges(edgeIndex)
              val angles = maybeSides.map(sides => RegularPolygon(sides).angles).getOrElse(EditorState.recentIrregularPolygon.now().get)
              val placement = FailedPolygonPlacement(edgeIndex, angles, (selectedEdge(0), selectedEdge(1)), tiling)
              val truncated = err.message
              if maybeSides.isDefined then
                ErrorOperations.showError(
                  s"Growing ${polygonName(maybeSides.get)}s on this perimeter edge is invalid. $truncated",
                  Some(placement)
                )
              else
                ErrorOperations.showError(
                  s"Growing the given ${angles.size}-sides irregular polygon on this perimeter edge is invalid. $truncated",
                  Some(placement)
                )
            else
              ErrorOperations.showError(err.message)
          }
        )

  // Helper: try to find the inner face that contains this directed edge; if not found, None
  private def findFaceContainingEdge(tiling: TilingDCEL, v1: VertexId, v2: VertexId): Option[FaceId] =
    // We look for a face whose boundary contains the directed edge (v1 -> v2).
    // If DCEL provides halfEdges on face with next pointers, prefer that; here we rely on vertex order on the face.
    tiling.innerFaces.find { face =>
      val ids = face.getVertices.toOption.get.map(_.id).toVector
      ids.slidingO(2).exists(pair => pair(0) == v1 && pair(1) == v2)
    }.map(_.id)

  def attemptPolygonInsertion(startVertexId: VertexId, endVertexId: VertexId): Unit =
    (currentTiling.now(), EditorState.selectedPolygon.now(), EditorState.isIrregularSelected.now()) match
      case (tiling, _, _) if tiling.isEmpty =>
        ErrorOperations.showError("No tiling available for insertion")
      case (_, None, false) =>
        Logger.warn("Both regular polygon and irregular polygon unselected")
      case (_, Some(_), true) =>
        Logger.error("Should not happen: both regular polygon and irregular polygon selected")
      case (tiling, maybeSides, _) =>
        val op = () =>
          try
            if maybeSides.isDefined then
              tiling.maybeAddRegularPolygon(startVertexId, endVertexId, maybeSides.get)
            else
              val angles = EditorState.recentIrregularPolygon.now().get.toList
              tiling.maybeAddSimplePolygon(startVertexId, endVertexId, angles)
          catch
            case e: Exception => Left(ValidationError(s"Error inserting polygon: ${e.getMessage}"))

        OperationRunner.runTilingOp(op)(
          onSuccess = {
            EditorState.selectedPerimeterEdges.set(Set.empty)
          },
          onFailure = error => {
            val curr = currentTiling.now()
            val maybeFaceId = findFaceContainingEdge(curr, startVertexId, endVertexId)
            if maybeSides.isDefined then
              val placementOpt =
                Some(
                  FailedPolygonPlacement(
                    edgeIndex = 0, // not needed for interior wireframe
                    angles = RegularPolygon(maybeSides.get).angles,
                    edge = (startVertexId, endVertexId),
                    tiling = curr,
                    intoFace = maybeFaceId
                  )
                )
              ErrorOperations.showError(s"Cannot insert regular polygon: ${error.message}", placement = placementOpt)
            else
              val placementOpt =
                Some(
                  FailedPolygonPlacement(
                    edgeIndex = 0, // not needed for interior wireframe
                    angles = EditorState.recentIrregularPolygon.now().get,
                    edge = (startVertexId, endVertexId),
                    tiling = curr,
                    intoFace = maybeFaceId
                  )
                )
              ErrorOperations.showError(s"Cannot insert irregular polygon: ${error.message}", placement = placementOpt)
          }
        )

  // Parse FaceId from a DOM polygon id of the form "tiling-poly-<faceId>"
  // Centralizing this at the operation boundary allows UI to keep legacy ids while core uses FaceId.
  private def parseFaceIdFromPolygonDomId(polygonId: String): Option[FaceId] =
    Some(FaceId(polygonId))
