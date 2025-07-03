package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.EditorState.{currentTiling, strictness}
import io.github.scala_tessella.editor.models.{EditorState, FailedPolygonDeletion, FailedPolygonPlacement}
import io.github.scala_tessella.editor.operations.ErrorOperations.showError
import io.github.scala_tessella.editor.utils.PolygonNameGenerator.polygonName
import io.github.scala_tessella.editor.utils.{AsyncUtils, TilingGenerator, UndoManager}

import io.github.scala_tessella.tessella.IncrementalTiling
import io.github.scala_tessella.tessella.Topology.NodeOrdering

import scala.concurrent.ExecutionContext.Implicits.global

object TessellationOperations:

  def selectPolygon(sides: Int): Unit =
    if !EditorState.isProcessing.now() then
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
    if !EditorState.isProcessing.now() then
      if !currentTiling.now().isEmpty then
        UndoManager.saveState()

      currentTiling.set(IncrementalTiling.empty)
      EditorState.polygonColors.set(Map.empty)
      EditorState.selectedTilingPolygons.set(Set.empty)
      EditorState.selectedPerimeterEdges.set(Set.empty)

  // Attempt to delete a polygon from the tessellation
  def attemptPolygonDeletion(polygonId: String): Unit =
    val future =
      AsyncUtils.withLoadingState { () =>
        currentTiling.now() match
          case tiling if !tiling.isEmpty =>
            // Extract polygon tag from the ID
            val polyTag = if polygonId.startsWith("tiling-poly-") then
              polygonId.substring("tiling-poly-".length)
            else
              polygonId

            // Find the specific polygon in the tiling
            val targetPolygon = tiling.orientedPolygons.find { nodes =>
              val tag = nodes.sorted(NodeOrdering).map(_.toString).mkString("-")
              tag == polyTag
            }

            targetPolygon match
              case Some(polygonNodes) =>
                // All checks passed - try actual deletion
                val result: Either[String, IncrementalTiling] =
                  tiling.removePolygon(polygonNodes)
                result match
                  case Right(newTiling) =>
                    // Success: return the new tiling
                    Right(newTiling)
                  case Left(errMsg) =>
                    // Failure: return error with wireframe info
                    Left(s"Cannot remove polygon: $errMsg")

              case None =>
                Left(s"Could not find polygon with tag: $polyTag")

          case _ =>
            Left("No tessellation available to modify")
      }

    future.foreach {
      case Right(newTiling) =>
        // Success: save state before change, then update tiling
        UndoManager.saveState()
        currentTiling.set(newTiling)
        ErrorOperations.clearError()
      case Left(errMsg) =>
        // Failure: show error with wireframe info
        val polygonNodes = currentTiling.now() match
          case tiling if !tiling.isEmpty =>
            val polyTag = if polygonId.startsWith("tiling-poly-") then
              polygonId.substring("tiling-poly-".length)
            else
              polygonId

            tiling.orientedPolygons.find { nodes =>
              val tag = nodes.sorted(NodeOrdering).map(_.toString).mkString("-")
              tag == polyTag
            }.getOrElse(Vector.empty)
          case _ => Vector.empty

        val failedDeletionInfo = FailedPolygonDeletion(polygonId, polygonNodes)
        ErrorOperations.showError(errMsg, deletion = Some(failedDeletionInfo))
    }

    future.recover {
      case ex: Exception =>
        ErrorOperations.showError(s"Error during polygon deletion: ${ex.getMessage}")
    }

  // Handle perimeter edge click with polygon growth
  def attemptPolygonAddition(edgeId: String, edgeIndex: Int): Unit =
    (currentTiling.now(), EditorState.selectedPolygon.now()) match
      case (tiling, _) if tiling.isEmpty=>
        ErrorOperations.showError("No tiling available to grow")
      case (tiling, Some(polygonSides)) =>
        // Try to grow the edge with the selected polygon
        AsyncUtils.withLoadingState { () =>
          try
            import io.github.scala_tessella.tessella.RegularPolygon.Polygon
            val polygon = Polygon(polygonSides)
            val perimeterEdges = tiling.perimeter.toEdgesO.toVector

            if edgeIndex < perimeterEdges.length then
              val selectedEdge = perimeterEdges(edgeIndex)
              tiling.addPolygon(polygon, selectedEdge, strictness.now())
            else
              Left("Invalid edge index")
          catch
            case e: Exception => Left(s"Error growing edge: ${e.getMessage}")
        }.foreach {
          case Right(newTiling) =>
            // Success: save state before change, then update tiling
            UndoManager.saveState()
            currentTiling.set(newTiling)
            EditorState.selectedPerimeterEdges.set(Set.empty)
            ErrorOperations.clearError()
          case Left(errMsg) =>
            // Failure: show error message with wireframe (no state to undo)
            val perimeterEdges = tiling.perimeter.toEdgesO.toVector
            if edgeIndex < perimeterEdges.length then
              val selectedEdge = perimeterEdges(edgeIndex)
              val placement = FailedPolygonPlacement(edgeIndex, polygonSides, selectedEdge, tiling)
              val truncated =
                val idx = errMsg.indexOf("See SVG")
                if idx >= 0 then errMsg.substring(0, idx)
                else errMsg
              ErrorOperations.showError(s"Growing ${polygonName(polygonSides)}s on this perimeter edge is invalid. Switch Validation OFF to proceed. $truncated", Some(placement))
            else
              ErrorOperations.showError(errMsg)
        }
      case (_, None) =>
        // This case should be handled by the caller
        ()