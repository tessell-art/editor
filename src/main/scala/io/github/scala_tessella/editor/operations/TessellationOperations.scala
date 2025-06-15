package io.github.scala_tessella.editor.operations

import io.github.scala_tessella.editor.utils.AsyncUtils.withLoadingState
import io.github.scala_tessella.editor.models.EditorState.currentTiling
import io.github.scala_tessella.editor.models.{EditorState, FailedPolygonDeletion, FailedPolygonPlacement}
import io.github.scala_tessella.editor.operations.ErrorOperations.{clearError, showError}
import io.github.scala_tessella.editor.utils.{AsyncUtils, TilingGenerator, UndoManager}
import io.github.scala_tessella.tessella.Tiling
import io.github.scala_tessella.tessella.TilingGrowth.OtherNodeStrategy.AFTER_PERIMETER
import io.github.scala_tessella.tessella.Topology.{Edge, NodeOrdering, Node as TilingNode}

import scala.concurrent.ExecutionContext.Implicits.global

object TessellationOperations:

  def selectPolygon(sides: Int): Unit =
    if !EditorState.isProcessing.now() then
      EditorState.selectedPolygon.set(Some(sides))

      if EditorState.currentTiling.now().isEmpty then
        UndoManager.saveState()

        AsyncUtils.withLoadingState { () =>
          TilingGenerator.createTilingFromPolygon(sides)
        }.foreach {
          case Some(tiling) =>
            EditorState.currentTiling.set(Some(tiling))
            SelectionOperations.clearAllSelections()
          case None =>
            UndoManager.undo()
            ErrorOperations.showError(s"Failed to create tiling from $sides-sided polygon")
        }

  def clearTiling(): Unit =
    if !EditorState.isProcessing.now() then
      if EditorState.currentTiling.now().nonEmpty then
        UndoManager.saveState()

      EditorState.currentTiling.set(None)
      EditorState.selectedTilingPolygons.set(Set.empty)
      EditorState.selectedPerimeterEdges.set(Set.empty)

  // Helper to check if edges form a continuous path
  def areEdgesContinuous(edges: Set[Edge]): Boolean =
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
