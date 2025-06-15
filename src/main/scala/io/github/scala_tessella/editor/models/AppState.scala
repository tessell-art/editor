package io.github.scala_tessella.editor.models

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.Tiling
import io.github.scala_tessella.tessella.TilingGrowth.OtherNodeStrategy.AFTER_PERIMETER
import io.github.scala_tessella.tessella.Topology.{Edge, NodeOrdering, Node as TilingNode}
import org.scalajs.dom
import io.github.scala_tessella.editor.utils.{TilingGenerator, UndoManager}
import io.github.scala_tessella.editor.models.EditorState.*
import io.github.scala_tessella.editor.utils.AsyncUtils.withLoadingState
import io.github.scala_tessella.editor.operations.ErrorOperations.{showError, clearError}

import scala.scalajs.js
import scala.util.Try
import scala.concurrent.{Future, Promise}
import scala.concurrent.ExecutionContext.Implicits.global

// Case class to represent a failed polygon placement
case class FailedPolygonPlacement(
                                   edgeIndex: Int,
                                   polygonSides: Int,
                                   edge: Edge,
                                   tiling: Tiling
                                 )

// Case class to represent a failed polygon deletion
case class FailedPolygonDeletion(
                                  polygonId: String,
                                  polygonNodes: Vector[TilingNode],
                                  tiling: Tiling
                                )

// Editor mode enumeration
enum EditorMode:
  case Select, Delete

object AppState:

  // Toggle editor mode between Select and Delete
  def toggleEditorMode(): Unit =
    if !isProcessing.now() then
      editorMode.update {
        case EditorMode.Select => EditorMode.Delete
        case EditorMode.Delete => EditorMode.Select
      }

  // Toggle node labels visibility
  def toggleNodeLabels(): Unit =
    if !isProcessing.now() then
      showNodeLabels.update(!_)

  // Polygon selection with tiling creation logic
  def selectPolygon(sides: Int): Unit =
    if !isProcessing.now() then
      selectedPolygon.set(Some(sides))

      // If tiling is empty, create a new tiling from the selected polygon
      if currentTiling.now().isEmpty then
        // Save state before creating new tiling
        UndoManager.saveState()

        withLoadingState { () =>
          TilingGenerator.createTilingFromPolygon(sides)
        }.foreach { result =>
          result match
            case Some(tiling) =>
              currentTiling.set(Some(tiling))
              clearAllSelections() // Clear selections when new tiling is created
            case None =>
              // Failed to create tiling - undo the saved state since operation failed
              UndoManager.undo()
              showError(s"Failed to create tiling from $sides-sided polygon")
        }

  // Check if tiling is empty
  def isTilingEmpty: Boolean = currentTiling.now().isEmpty

  // Clear tiling and reset to empty state
  def clearTiling(): Unit =
    if !isProcessing.now() then
      // Save state before clearing
      if currentTiling.now().nonEmpty then
        UndoManager.saveState()

      currentTiling.set(None)
      selectedTilingPolygons.set(Set.empty)
      selectedPerimeterEdges.set(Set.empty)

  // Clear all selections
  def clearAllSelections(): Unit =
    if !isProcessing.now() then
      selectedTilingPolygons.set(Set.empty)
      selectedPerimeterEdges.set(Set.empty)

  // Handle tiling polygon click based on current editor mode
  def handleTilingPolygonClick(polygonId: String): Unit =
    if !isProcessing.now() then
      editorMode.now() match
        case EditorMode.Select =>
          toggleTilingPolygonSelection(polygonId)
        case EditorMode.Delete =>
          attemptPolygonDeletion(polygonId)

  // Toggle tiling polygon selection
  def toggleTilingPolygonSelection(polygonId: String): Unit =
    if !isProcessing.now() then
      selectedTilingPolygons.update { selected =>
        if selected.contains(polygonId) then selected - polygonId
        else selected + polygonId
      }

  // Helper to check if edges form a continuous path
  private def areEdgesContinuous(edges: Set[Edge]): Boolean =
    if edges.isEmpty then return true
    if edges.size == 1 then return true

    // Build adjacency map
    val adjacency = scala.collection.mutable.Map[TilingNode, Set[TilingNode]]()
    edges.foreach { edge =>
      val lesser: TilingNode = edge.lesserNode
      val greater: TilingNode = edge.greaterNode
      adjacency(lesser) = adjacency.getOrElse(lesser, Set.empty) + greater
      adjacency(greater) = adjacency.getOrElse(greater, Set.empty) + lesser
    }

    // For edges to form a continuous path, each node should have at most 2 connections
    // and all edges should be connected in a single path or cycle
    val nodeDegrees = adjacency.view.mapValues(_.size).toMap

    // Check if any node has more than 2 connections (would mean branching)
    if nodeDegrees.values.exists(_ > 2) then return false

    // Check connectivity - start from any node and traverse
    val startNode = adjacency.keys.head
    val visited = scala.collection.mutable.Set[TilingNode]()
    val queue = scala.collection.mutable.Queue[TilingNode](startNode)

    while queue.nonEmpty do
      val current = queue.dequeue()
      if !visited.contains(current) then
        visited.add(current)
        adjacency.get(current).foreach { neighbors =>
          neighbors.foreach { neighbor =>
            if !visited.contains(neighbor) then
              queue.enqueue(neighbor)
          }
        }

    // All nodes involved in edges should be visited
    visited.size == adjacency.size

  // Attempt to delete a polygon from the tessellation
  private def attemptPolygonDeletion(polygonId: String): Unit =
    val future = withLoadingState { () =>
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

              // Create failed deletion object for wireframe effect
              val failedDeletionInfo = FailedPolygonDeletion(polygonId, polygonNodes, tiling)

              if edgesOnPerimeter.isEmpty then
                Left(s"Cannot delete polygon $polyTag: No perimeter edges found. Internal polygons cannot be deleted as it would create holes in the tessellation.")
              else
                // Check if perimeter edges form a continuous path
                if !areEdgesContinuous(edgesOnPerimeter) then
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

    future.foreach { result =>
      result match
        case Right(newTiling) =>
          // Success: save state before change, then update tiling
          UndoManager.saveState()
          currentTiling.set(Some(newTiling))
          clearError()
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
          showError(errMsg, deletion = Some(failedDeletionInfo))
    }

    future.recover {
      case ex: Exception =>
        showError(s"Error during polygon deletion: ${ex.getMessage}")
    }
  // Toggle perimeter edge selection
  def togglePerimeterEdgeSelection(edgeId: String): Unit =
    if !isProcessing.now() then
      selectedPerimeterEdges.update { selected =>
        if selected.contains(edgeId) then selected - edgeId
        else selected + edgeId
      }

  // Handle perimeter edge click with polygon growth
  def handlePerimeterEdgeClick(edgeId: String, edgeIndex: Int): Unit =
    if !isProcessing.now() then
      (currentTiling.now(), selectedPolygon.now()) match
        case (Some(tiling), Some(polygonSides)) =>
          // Try to grow the edge with the selected polygon
          withLoadingState { () =>
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
          }.foreach { result =>
            result match
              case Right(newTiling) =>
                // Success: save state before change, then update tiling
                UndoManager.saveState()
                currentTiling.set(Some(newTiling))
                selectedPerimeterEdges.set(Set.empty)
                clearError()
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
                  showError(s"Cannot grow edge with $polygonSides-sided polygon: $truncated", Some(placement))
                else
                  showError(errMsg)
          }
        case (None, _) =>
          showError("No tiling available to grow")
        case (_, None) =>
          // No polygon selected, just toggle selection
          togglePerimeterEdgeSelection(edgeId)

  // Delete selected elements (only applies to tiling polygons now)
  def deleteSelectedElements(): Unit =
    if !isProcessing.now() then
      // For now, we don't support deleting parts of tessellations
      // This could be enhanced later to support tiling modifications
      if (selectedTilingPolygons.now().nonEmpty) then
        showError("Tessellation polygon deletion not supported yet")

  def undo(): Unit =
    if !isProcessing.now() then
      UndoManager.undo()