package io.github.scala_tessella.editor.models

import com.raquo.laminar.api.L.{*, given}
import io.github.scala_tessella.tessella.Tiling
import io.github.scala_tessella.tessella.TilingGrowth.OtherNodeStrategy.AFTER_PERIMETER
import io.github.scala_tessella.tessella.Topology.{Edge, NodeOrdering, Node as TilingNode}
import org.scalajs.dom
import io.github.scala_tessella.editor.utils.TilingGenerator

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
  // Polygon palette state
  val polygonSides: List[Int] = List(3, 4, 5, 6, 7, 8, 9, 10, 12, 15, 18, 20, 24, 42)
  val selectedPolygon: Var[Option[Int]] = Var[Option[Int]](None)

  // Canvas state - simplified to only include view transform
  val viewTransform: Var[ViewTransform] = Var(ViewTransform())

  // Editor mode state
  val editorMode: Var[EditorMode] = Var(EditorMode.Select)

  // Loading state for expensive operations
  val isProcessing: Var[Boolean] = Var(false)

  // Tessellation state - start with empty tiling
  val currentTiling: Var[Option[Tiling]] = Var(None)
  val selectedPerimeterEdges: Var[Set[String]] = Var(Set.empty)
  val selectedTilingPolygons: Var[Set[String]] = Var(Set.empty)

  val fillColor: Var[(Int, Int, Int)] = Var((76, 175, 80)) // Default: Material green (R,G,B)
  val polygonColors: Var[Map[String, (Int, Int, Int)]] = Var(Map.empty)

  // Visualization state
  val showNodeLabels: Var[Boolean] = Var(false)

  // Error message state
  val errorMessage: Var[Option[String]] = Var(None)

  // Failed polygon placement state - for showing wireframe feedback
  val failedPlacement: Var[Option[FailedPolygonPlacement]] = Var(None)

  // Failed polygon deletion state - for showing wireframe feedback
  val failedDeletion: Var[Option[FailedPolygonDeletion]] = Var(None)

  // Canvas interaction state
  val isDragging: Var[Boolean] = Var(false)
  val dragStart: Var[Option[Point]] = Var(None)
  val canvasElementRef: Var[Option[dom.Element]] = Var(None)

  // JavaScript-compatible delay function [[2]](https://stackoverflow.com/questions/46617946/sleep-inside-future-in-scala-js)
  private def delay(milliseconds: Int): Future[Unit] = {
    val p = Promise[Unit]()
    js.timers.setTimeout(milliseconds) {
      p.success(())
    }
    p.future
  }

  // Helper to execute expensive operations with loading state
  private def withLoadingState[T](operation: () => T): Future[T] = {
    isProcessing.set(true)

    // Use delay instead of Thread.sleep to let UI update before computation
    delay(50).map { _ =>
      try {
        operation()
      } finally {
        isProcessing.set(false)
      }
    }
  }

  // Toggle editor mode between Select and Delete
  def toggleEditorMode(): Unit =
    if !isProcessing.now() then
      editorMode.update {
        case EditorMode.Select => EditorMode.Delete
        case EditorMode.Delete => EditorMode.Select
      }

  // Apply color to selected polygons
  def applyColorToSelectedPolygons(color: (Int, Int, Int)): Unit =
    if !isProcessing.now() then
      val selectedIds = selectedTilingPolygons.now()
      if selectedIds.nonEmpty then
        // Extract polygon tags from the selected polygon IDs
        val selectedTags = selectedIds.map { id =>
          // Remove "tiling-poly-" prefix to get the polygon tag
          if id.startsWith("tiling-poly-") then id.substring("tiling-poly-".length)
          else id
        }

        // Update colors for selected polygon tags
        polygonColors.update { currentColors =>
          selectedTags.foldLeft(currentColors) { (colors, tag) =>
            colors + (tag -> color)
          }
        }

  // Toggle node labels visibility
  def toggleNodeLabels(): Unit =
    if !isProcessing.now() then
      showNodeLabels.update(!_)

  // Show error message temporarily with optional failed placement info
  def showError(message: String, placement: Option[FailedPolygonPlacement] = None, deletion: Option[FailedPolygonDeletion] = None): Unit =
    errorMessage.set(Some(message))
    failedPlacement.set(placement)
    failedDeletion.set(deletion)

    // Clear error and failed placement/deletion after 3 seconds
    Try {
      if (js.typeOf(js.Dynamic.global.window) != "undefined") {
        dom.window.setTimeout(() => {
          errorMessage.set(None)
          failedPlacement.set(None)
          failedDeletion.set(None)
        }, 3000)
      }
    }.recover {
      case _ => // Ignore errors in test environment
    }

  // Clear error message and failed placement
  def clearError(): Unit =
    errorMessage.set(None)
    failedPlacement.set(None)
    failedDeletion.set(None)

  // Polygon selection with tiling creation logic
  def selectPolygon(sides: Int): Unit =
    if !isProcessing.now() then
      selectedPolygon.set(Some(sides))

      // If tiling is empty, create a new tiling from the selected polygon
      if currentTiling.now().isEmpty then
        withLoadingState { () =>
          TilingGenerator.createTilingFromPolygon(sides)
        }.foreach {
          case Some(tiling) =>
            currentTiling.set(Some(tiling))
            clearAllSelections() // Clear selections when new tiling is created
          case None =>
            showError(s"Failed to create tiling from $sides-sided polygon")
        }

  // Check if tiling is empty
  def isTilingEmpty: Boolean = currentTiling.now().isEmpty

  // Clear tiling and reset to empty state
  def clearTiling(): Unit =
    if !isProcessing.now() then
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
    withLoadingState { () =>
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
                showError(s"Cannot delete polygon $polyTag: No perimeter edges found. Internal polygons cannot be deleted as it would create holes in the tessellation.", deletion = Some(failedDeletionInfo))
              else
                // Check if perimeter edges form a continuous path
                if !areEdgesContinuous(edgesOnPerimeter) then
                  val edgeList = edgesOnPerimeter.map(edge => s"${edge.lesserNode}-${edge.greaterNode}").mkString(", ")
                  showError(s"Cannot delete polygon $polyTag: Perimeter edges ($edgeList) do not form a continuous path. Deletion would split the tessellation.", deletion = Some(failedDeletionInfo))
                else
                  // Check if there are polygon nodes on perimeter that are not part of the found edges
                  val nodesInPerimeterEdges = edgesOnPerimeter.flatMap(edge => Set(edge.lesserNode, edge.greaterNode))
                  val isolatedPerimeterNodes = nodesOnPerimeter -- nodesInPerimeterEdges

                  if isolatedPerimeterNodes.nonEmpty then
                    val nodeList = isolatedPerimeterNodes.mkString(", ")
                    val edgeList = edgesOnPerimeter.map(edge => s"${edge.lesserNode}-${edge.greaterNode}").mkString(", ")
                    showError(s"Cannot delete polygon $polyTag: Has isolated perimeter nodes ($nodeList) not connected to perimeter edges ($edgeList). Deletion would split the tessellation.", deletion = Some(failedDeletionInfo))
                  else
                    // All checks passed - try actual deletion
                    val result: Either[String, Tiling] =
                      Tiling.maybe(tiling.graphEdges.diff(edgesOnPerimeter.toSeq))
                    result match
                      case Right(newTiling) =>
                        // Success: update tiling and clear selections
                        currentTiling.set(Some(newTiling))
                        clearError()
                      case Left(errMsg) =>
                        // Failure: show an error message
                        showError(s"Cannot remove polygon: $errMsg", deletion = Some(failedDeletionInfo))

            case None =>
              showError(s"Could not find polygon with tag: $polyTag")

        case None =>
          showError("No tessellation available to modify")
    }.recover {
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
                // Success: update tiling and clear selections
                currentTiling.set(Some(newTiling))
                selectedPerimeterEdges.set(Set.empty)
                clearError()
              case Left(errMsg) =>
                // Failure: show error message with wireframe
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

  // Helper to get or generate a unique RGB color for a given polygon id
  def getOrAssignPolygonColor(polyTag: String): (Int, Int, Int) =
    polygonColors.now().get(polyTag) match
      case Some(rgb) => rgb
      case None =>
        val rgb = fillColor.now()
        polygonColors.update(_ + (polyTag -> rgb))
        rgb