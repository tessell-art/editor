
package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.models.EditorState.{currentTiling, previousTiling}
import io.github.scala_tessella.editor.models.{EditorState, FailedPolygonDeletion, FailedPolygonPlacement}
import io.github.scala_tessella.editor.operations.ErrorOperations.showError
import io.github.scala_tessella.editor.utils.{TilingGenerator, UndoManager, AsyncUtils}
import io.github.scala_tessella.tessella.Tiling
import io.github.scala_tessella.tessella.TilingGrowth.OtherNodeStrategy.AFTER_PERIMETER
import io.github.scala_tessella.tessella.Topology.{Edge, NodeOrdering}

import scala.concurrent.ExecutionContext.Implicits.global

object TessellationOperations:

  def updateTiling(newTiling: Option[Tiling]): Unit =
    val current = currentTiling.now()
    previousTiling.set(current)
    currentTiling.set(newTiling)

  def selectPolygon(sides: Int): Unit =
    if !EditorState.isProcessing.now() then
      EditorState.selectedPolygon.set(Some(sides))

      if currentTiling.now().isEmpty then
        UndoManager.saveState()
        TilingGenerator.createTilingFromPolygon(sides) match
          case Some(tiling) =>
            updateTiling(Some(tiling))
            SelectionOperations.clearAllSelections()
          case None =>
            UndoManager.undo()
            ErrorOperations.showError(s"Failed to create tiling from $sides-sided polygon")

  def clearTiling(): Unit =
    if !EditorState.isProcessing.now() then
      if currentTiling.now().nonEmpty then
        UndoManager.saveState()

      updateTiling(None)
      EditorState.polygonColors.set(Map.empty)
      EditorState.selectedTilingPolygons.set(Set.empty)
      EditorState.selectedPerimeterEdges.set(Set.empty)

  // Attempt to delete a polygon from the tessellation
  def attemptPolygonDeletion(polygonId: String): Unit =
    val future =
      AsyncUtils.withLoadingState { () =>
        currentTiling.now() match
          case Some(tiling) =>
            // Extract polygon tag from the ID
            val polyTag = if polygonId.startsWith("tiling-poly-") then
              polygonId.substring("tiling-poly-".length)
            else
              polygonId

            // Find the specific polygon in the tiling
            val targetPolygon = tiling.orientedPolygons.find { poly =>
              val nodes = poly.toPolygonPathNodes
              val tag = nodes.sorted(NodeOrdering).map(_.toString).mkString("-")
              tag == polyTag
            }

            targetPolygon match
              case Some(polygon) =>
                // Get the polygon's nodes and edges
                val polygonNodes = polygon.toPolygonPathNodes
                val polygonNodesSet = polygonNodes.toSet
                val polygonEdges = polygonNodes.zipWithIndex.map { case (node, i) =>
                  val nextNode = polygonNodes((i + 1) % polygonNodes.length)
                  Edge(node, nextNode)
                }.toSet

                // Get perimeter edges and perimeter nodes
                val perimeterEdges = tiling.perimeter.toRingEdges.toSet
                val perimeterNodes = perimeterEdges.flatMap(edge => Set(edge.lesserNode, edge.greaterNode))

                // Find which polygon edges are on the perimeter
                val edgesOnPerimeter: Set[Edge] = polygonEdges.intersect(perimeterEdges)

                // Find which polygon nodes are on the perimeter
                val nodesOnPerimeter = polygonNodesSet.intersect(perimeterNodes)

                if edgesOnPerimeter.isEmpty then
                  Left(s"Cannot delete polygon $polyTag: No perimeter edges found. Internal polygons cannot be deleted as it would create holes in the tessellation.")
                else
                  // Check if perimeter edges form a continuous path
                  if !edgesOnPerimeter.toList.areContinuous then
                    val edgeList = edgesOnPerimeter.map(edge => s"${edge.lesserNode}-${edge.greaterNode}").mkString(", ")
                    Left(s"Cannot delete polygon $polyTag: Perimeter edges ($edgeList) do not form a continuous path. Deletion would split the tessellation.")
                  else
                    // Check if there are polygon nodes on perimeter that are not part of the found edges
                    val nodesInPerimeterEdges = edgesOnPerimeter.flatMap(edge => Set(edge.lesserNode, edge.greaterNode))
                    val isolatedPerimeterNodes = nodesOnPerimeter -- nodesInPerimeterEdges

                    if isolatedPerimeterNodes.nonEmpty then
                      val nodeList = isolatedPerimeterNodes.mkString(", ")
                      val edgeList = edgesOnPerimeter.map(edge => s"${edge.lesserNode}-${edge.greaterNode}").mkString(", ")
                      Left(s"Cannot delete polygon $polyTag: Has isolated perimeter nodes ($nodeList) not connected to perimeter edges ($edgeList). Deletion would split the tessellation.")
                    else
                      // All checks passed - try actual deletion
                      val result: Either[String, Tiling] =
                        Tiling.maybe(tiling.graphEdges.diff(edgesOnPerimeter.toSeq))
                      result match
                        case Right(newTiling) =>
                          // Success: return the new tiling
                          Right(newTiling)
                        case Left(errMsg) =>
                          // Failure: return error with wireframe info
                          Left(s"Cannot remove polygon: $errMsg")

              case None =>
                Left(s"Could not find polygon with tag: $polyTag")

          case None =>
            Left("No tessellation available to modify")
      }

    future.foreach {
      case Right(newTiling) =>
        // Success: save state before change, then update tiling
        UndoManager.saveState()
        updateTiling(Some(newTiling))
        ErrorOperations.clearError()
      case Left(errMsg) =>
        // Failure: show error with wireframe info
        val polygonNodes = currentTiling.now().flatMap { tiling =>
          val polyTag = if polygonId.startsWith("tiling-poly-") then
            polygonId.substring("tiling-poly-".length)
          else
            polygonId

          tiling.orientedPolygons.find { poly =>
            val nodes = poly.toPolygonPathNodes
            val tag = nodes.sorted(NodeOrdering).map(_.toString).mkString("-")
            tag == polyTag
          }.map(_.toPolygonPathNodes)
        }.getOrElse(Vector.empty)

        val failedDeletionInfo = FailedPolygonDeletion(polygonId, polygonNodes, currentTiling.now().get)
        ErrorOperations.showError(errMsg, deletion = Some(failedDeletionInfo))
    }

    future.recover {
      case ex: Exception =>
        ErrorOperations.showError(s"Error during polygon deletion: ${ex.getMessage}")
    }

  // Handle perimeter edge click with polygon growth
  def attemptPolygonGrowth(edgeId: String, edgeIndex: Int): Unit =
    (currentTiling.now(), EditorState.selectedPolygon.now()) match
      case (Some(tiling), Some(polygonSides)) =>
        // Try to grow the edge with the selected polygon
        AsyncUtils.withLoadingState { () =>
          try
            import io.github.scala_tessella.tessella.RegularPolygon.Polygon
            val polygon = Polygon(polygonSides)
            val perimeterEdges = tiling.perimeter.toRingEdges.toVector

            if edgeIndex < perimeterEdges.length then
              val selectedEdge = perimeterEdges(edgeIndex)
              tiling.maybeGrowEdge(selectedEdge, polygon, AFTER_PERIMETER)
            else
              Left("Invalid edge index")
          catch
            case e: Exception => Left(s"Error growing edge: ${e.getMessage}")
        }.foreach {
          case Right(newTiling) =>
            // Success: save state before change, then update tiling
            UndoManager.saveState()
            updateTiling(Some(newTiling))
            EditorState.selectedPerimeterEdges.set(Set.empty)
            ErrorOperations.clearError()
          case Left(errMsg) =>
            // Failure: show error message with wireframe (no state to undo)
            val perimeterEdges = tiling.perimeter.toRingEdges.toVector
            if edgeIndex < perimeterEdges.length then
              val selectedEdge = perimeterEdges(edgeIndex)
              val placement = FailedPolygonPlacement(edgeIndex, polygonSides, selectedEdge, tiling)
              val truncated =
                val idx = errMsg.indexOf("See SVG")
                if idx >= 0 then errMsg.substring(0, idx)
                else errMsg
              ErrorOperations.showError(s"Cannot grow edge with $polygonSides-sided polygon: $truncated", Some(placement))
            else
              ErrorOperations.showError(errMsg)
        }
      case (None, _) =>
        ErrorOperations.showError("No tiling available to grow")
      case (_, None) =>
        // This case should be handled by the caller
        ()